package com.sa.assistant.common.lock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 基于 Redis SET NX + Lua 的分布式锁实现，支持 WatchDog 自动续期。
 *
 * <h3>竞态分析</h3>
 * <pre>
 *   问题：业务执行时间 > 锁 TTL → 锁到期自动释放 → 其他线程获取锁 → 互斥失效
 *
 *   解锁安全性（不会误删）：
 *     解锁使用 Lua 原子脚本：get(key) == requestId 时才 del(key)
 *     即使锁已过期被他人持有，get 返回的 requestId 不匹配 → 脚本返回 0 → 不删除。
 *     因此不会误删他人的锁 —— 但这只保证了解锁的安全性，不保证互斥。
 *
 *   互斥保证：
 *     引入 WatchDog（看门狗）续期机制：
 *     每 expireSeconds/3 秒自动续期一次，确保业务不结束、锁不过期。
 * </pre>
 */
@Slf4j
@Component
public class DistributedLock {

    private final StringRedisTemplate stringRedisTemplate;

    // ---- Lua scripts ----

    /**
     * 解锁脚本：仅当 value 匹配时才删除 key，防止误删他人持有的锁。
     */
    private static final String UNLOCK_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;

    /**
     * 续期脚本：仅当 value 匹配时才延长 TTL，防止续期已过期/被他人持有的锁。
     */
    private static final String RENEW_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('expire', KEYS[1], ARGV[2])
            else
                return 0
            end
            """;

    private final DefaultRedisScript<Long> unlockScript =
            new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);

    private final DefaultRedisScript<Long> renewScript =
            new DefaultRedisScript<>(RENEW_SCRIPT, Long.class);

    /**
     * WatchDog 调度线程池。
     * 使用守护线程，确保 JVM 退出时不会阻塞。
     * 核心线程数 2，最大 4 —— 续期是轻量 Redis 操作，此配置足够。
     */
    private final ScheduledExecutorService watchdogExecutor;

    public DistributedLock(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(2);
        executor.setMaximumPoolSize(4);
        executor.setThreadFactory(r -> {
            Thread t = new Thread(r, "lock-watchdog");
            t.setDaemon(true);
            return t;
        });
        executor.setRemoveOnCancelPolicy(true);
        this.watchdogExecutor = executor;
    }

    // ---- 基础操作 ----

    public boolean tryLock(String lockKey, String requestId, long expireSeconds) {
        Boolean result = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, requestId, expireSeconds, TimeUnit.SECONDS);
        boolean locked = Boolean.TRUE.equals(result);
        if (locked) {
            log.debug("Lock acquired | key={}, holder={}, ttl={}s", lockKey, requestId, expireSeconds);
        }
        return locked;
    }

    public boolean unlock(String lockKey, String requestId) {
        Long result = stringRedisTemplate.execute(
                unlockScript,
                Collections.singletonList(lockKey),
                requestId
        );
        boolean released = Long.valueOf(1L).equals(result);
        if (released) {
            log.debug("Lock released | key={}, holder={}", lockKey, requestId);
        } else {
            log.warn("Lock release skipped (not owner or expired) | key={}, holder={}", lockKey, requestId);
        }
        return released;
    }

    public String generateRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // ---- 简单加锁（无续期，保持向后兼容） ----

    public <T> T executeWithLock(String lockKey, long expireSeconds, Supplier<T> action) {
        String requestId = generateRequestId();
        boolean locked = tryLock(lockKey, requestId, expireSeconds);
        if (!locked) {
            log.warn("Failed to acquire lock | key={}", lockKey);
            throw new LockAcquisitionException("操作过于频繁，请稍后重试");
        }
        try {
            return action.get();
        } finally {
            unlock(lockKey, requestId);
        }
    }

    public void executeWithLock(String lockKey, long expireSeconds, Runnable action) {
        executeWithLock(lockKey, expireSeconds, () -> {
            action.run();
            return null;
        });
    }

    // ---- WatchDog 续期锁（推荐使用） ----

    /**
     * 获取锁并启动 WatchDog 自动续期。
     *
     * <p>续期策略：每 expireSeconds/3 秒续期一次，续期失败立即停止。
     * 这意味着在锁过期前至少有 3 次续期机会，容忍网络偶发抖动。
     *
     * <p>使用示例：
     * <pre>{@code
     *   distributedLock.executeWithLockWatchDog("lock:myKey", 10, () -> {
     *       // 耗时可能超过 10s 的业务逻辑，WatchDog 会自动续期
     *       longRunningTask();
     *       return result;
     *   });
     * }</pre>
     *
     * @param lockKey      锁的 Redis key
     * @param expireSeconds 锁的初始过期时间（秒），同时也是续期目标 TTL
     * @param action        需要互斥保护的业务逻辑
     */
    public <T> T executeWithLockWatchDog(String lockKey, long expireSeconds, Supplier<T> action) {
        String requestId = generateRequestId();
        boolean locked = tryLock(lockKey, requestId, expireSeconds);
        if (!locked) {
            log.warn("Failed to acquire lock | key={}", lockKey);
            throw new LockAcquisitionException("操作过于频繁，请稍后重试");
        }

        WatchDog watchDog = new WatchDog(lockKey, requestId, expireSeconds);
        watchDog.start();

        try {
            return action.get();
        } finally {
            watchDog.stop();
            unlock(lockKey, requestId);
        }
    }

    public void executeWithLockWatchDog(String lockKey, long expireSeconds, Runnable action) {
        executeWithLockWatchDog(lockKey, expireSeconds, () -> {
            action.run();
            return null;
        });
    }

    // ---- 带重试的加锁（不支持续期，保持向后兼容） ----

    public <T> T executeWithLockRetry(String lockKey, long expireSeconds, int maxRetries,
                                      long retryIntervalMs, Supplier<T> action) {
        String requestId = generateRequestId();
        int attempts = 0;

        while (attempts <= maxRetries) {
            boolean locked = tryLock(lockKey, requestId, expireSeconds);
            if (locked) {
                try {
                    return action.get();
                } finally {
                    unlock(lockKey, requestId);
                }
            }
            attempts++;
            if (attempts <= maxRetries) {
                log.debug("Lock retry | key={}, attempt={}/{}", lockKey, attempts, maxRetries);
                try {
                    Thread.sleep(retryIntervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new LockAcquisitionException("获取锁被中断");
                }
            }
        }

        log.warn("Lock acquisition failed after retries | key={}, maxRetries={}", lockKey, maxRetries);
        throw new LockAcquisitionException("操作过于频繁，请稍后重试");
    }

    // ---- WatchDog 内部类 ----

    /**
     * WatchDog（看门狗）：定时向 Redis 续期锁的 TTL。
     *
     * <h3>设计要点</h3>
     * <ul>
     *   <li><b>续期间隔</b> = expireSeconds / 3，在锁过期前可续期 3 次，容忍偶发网络抖动</li>
     *   <li><b>续期失败立即停止</b> —— 说明锁已不存在（过期或被他人持有），继续续期无意义</li>
     *   <li><b>finally 中 stop()</b> —— 无论业务正常/异常结束，WatchDog 都会被取消</li>
     *   <li><b>守护线程</b> —— JVM 退出时不会阻塞</li>
     * </ul>
     */
    private class WatchDog {
        private final String lockKey;
        private final String requestId;
        private final long renewTargetTtlSeconds;
        private final long renewIntervalSeconds;
        private final AtomicInteger renewalCount = new AtomicInteger(0);
        private volatile ScheduledFuture<?> future;
        private volatile boolean stopped = false;

        WatchDog(String lockKey, String requestId, long expireSeconds) {
            this.lockKey = lockKey;
            this.requestId = requestId;
            this.renewTargetTtlSeconds = expireSeconds;
            // 每 expireSeconds/3 续期一次，最少 1 秒
            this.renewIntervalSeconds = Math.max(1, expireSeconds / 3);
        }

        void start() {
            future = watchdogExecutor.scheduleAtFixedRate(
                    this::renew,
                    renewIntervalSeconds,
                    renewIntervalSeconds,
                    TimeUnit.SECONDS
            );
            log.debug("WatchDog started | key={}, interval={}s, targetTtl={}s",
                    lockKey, renewIntervalSeconds, renewTargetTtlSeconds);
        }

        void stop() {
            stopped = true;
            if (future != null && !future.isCancelled()) {
                future.cancel(false);
                log.debug("WatchDog stopped | key={}, renewals={}", lockKey, renewalCount.get());
            }
        }

        private void renew() {
            if (stopped) {
                return;
            }

            try {
                Long result = stringRedisTemplate.execute(
                        renewScript,
                        Collections.singletonList(lockKey),
                        requestId,
                        String.valueOf(renewTargetTtlSeconds)
                );

                if (Long.valueOf(1L).equals(result)) {
                    int count = renewalCount.incrementAndGet();
                    log.debug("WatchDog renewed | key={}, ttl={}s, count={}",
                            lockKey, renewTargetTtlSeconds, count);
                } else {
                    // 续期返回 0 说明锁已不属于当前持有者（过期/被抢占）
                    log.warn("WatchDog renewal denied (lock lost) | key={}, renter={}",
                            lockKey, requestId);
                    stop();
                }
            } catch (Exception e) {
                log.error("WatchDog renewal failed | key={}, error={}", lockKey, e.getMessage());
                // 不停止 —— 单次网络错误不应终止 WatchDog，下一次调度会重试
            }
        }
    }
}
