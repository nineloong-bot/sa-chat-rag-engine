package com.sa.assistant.cache;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class MultiLevelCache extends AbstractValueAdaptingCache {

    private final String name;
    private final Cache<Object, Object> caffeineCache;
    private final RedisTemplate<String, Object> redisTemplate;
    private final String redisKeyPrefix;
    private final long redisTtlMillis;

    /*
     * 防缓存击穿的本地锁表。
     *
     * 缓存击穿（Cache Breakdown）：某个热点 key 过期的瞬间，大量并发请求同时穿透到数据库。
     * 解决方案：对同一个 key 的回源操作加本地锁，只允许一个线程查数据库，其余线程等待结果。
     * 为什么用本地锁而不是分布式锁？因为 L1（Caffeine）是进程内的，本地锁足够且零网络开销。
     * 对于 L2（Redis）的击穿防护，依赖 Redis 的 SETNX 实现分布式互斥。
     */
    private final ConcurrentHashMap<Object, ReentrantLock> keyLocks = new ConcurrentHashMap<>();

    public MultiLevelCache(String name,
                           Cache<Object, Object> caffeineCache,
                           RedisTemplate<String, Object> redisTemplate,
                           String redisKeyPrefix,
                           long redisTtlMillis) {
        super(true);
        this.name = name;
        this.caffeineCache = caffeineCache;
        this.redisTemplate = redisTemplate;
        this.redisKeyPrefix = redisKeyPrefix;
        this.redisTtlMillis = redisTtlMillis;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return caffeineCache;
    }

    /*
     * 多级缓存读取核心流程：L1 → L2 → DB
     *
     * 1. 先查 Caffeine（L1），命中直接返回（纳秒级）
     * 2. L1 未命中，查 Redis（L2），命中则回填 L1 并返回（毫秒级）
     * 3. L2 也未命中，返回 null，由调用方触发 DB 回源
     *
     * 防缓存雪崩（Cache Avalanche）的设计：
     *   - L1 的 expireAfterWrite 使用随机偏移（在 Caffeine 配置中已设置基础过期时间）
     *   - L2 的 TTL 在基础值上加入 0~30s 的随机抖动（见 buildRedisKey 方法注释）
     *   - 这样避免了大量 key 在同一时刻集体过期，导致请求全部打到 DB
     */
    @Override
    protected Object lookup(Object key) {
        String redisKey = buildRedisKey(key);

        // Step 1: 查 L1 - Caffeine
        Object l1Value = caffeineCache.getIfPresent(key);
        if (l1Value != null) {
            log.debug("Cache L1 HIT | cache={}, key={}", name, key);
            return l1Value;
        }

        // Step 2: 查 L2 - Redis
        Object l2Value = redisTemplate.opsForValue().get(redisKey);
        if (l2Value != null) {
            log.debug("Cache L2 HIT | cache={}, key={}", name, key);
            // 回填 L1：下次同进程请求可直接命中 L1
            caffeineCache.put(key, l2Value);
            return l2Value;
        }

        log.debug("Cache MISS | cache={}, key={}", name, key);
        return null;
    }

    /*
     * 写入多级缓存：同时写入 L1 和 L2
     *
     * L2 的 TTL 加入随机抖动（0~30s），防止缓存雪崩。
     * 原理：如果 1000 个 key 的 TTL 都是精确的 20 分钟，
     * 那么第 20 分钟时它们会同时过期 → 所有请求同时打到 DB。
     * 加入随机偏移后，过期时间分散在 20~20.5 分钟之间，错开峰值。
     */
    @Override
    public void put(Object key, Object value) {
        String redisKey = buildRedisKey(key);

        // 写入 L1
        caffeineCache.put(key, value);

        // 写入 L2，TTL 加入随机抖动防雪崩
        long jitter = (long) (Math.random() * 30_000);
        long finalTtl = redisTtlMillis + jitter;
        redisTemplate.opsForValue().set(redisKey, value, finalTtl, TimeUnit.MILLISECONDS);

        log.debug("Cache PUT | cache={}, key={}, ttl={}ms", name, key, finalTtl);
    }

    /*
     * 带 Callable 回源的读取（Spring Cache @Cacheable 的核心调用入口）
     *
     * 防缓存击穿的关键逻辑：
     *   1. 先走 lookup() 尝试 L1 → L2
     *   2. 如果都未命中，对同一个 key 加本地锁（ReentrantLock）
     *   3. 加锁后 double-check：可能其他线程已经回源并填充了缓存
     *   4. 只有一个线程执行 valueLoader.call() 查数据库
     *   5. 其他等待线程直接获取已填充的缓存值
     */
    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        Object value = lookup(key);
        if (value != null) {
            return (T) fromStoreValue(value);
        }

        // 防击穿：对同一个 key 加锁，只允许一个线程回源
        ReentrantLock lock = keyLocks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {
            // Double-check：加锁后再查一次，可能其他线程已回源
            value = lookup(key);
            if (value != null) {
                return (T) fromStoreValue(value);
            }

            // 真正回源查数据库
            T loaded = valueLoader.call();
            put(key, toStoreValue(loaded));
            return loaded;
        } catch (Exception e) {
            log.error("Cache valueLoader failed | cache={}, key={}", name, key, e);
            throw new RuntimeException("Cache valueLoader failed", e);
        } finally {
            lock.unlock();
            keyLocks.remove(key);
        }
    }

    private static final int EVICT_MAX_RETRIES = 3;
    private static final long EVICT_RETRY_BASE_DELAY_MS = 200;

    /*
     * 删除缓存：先删 L2(Redis)，再删 L1(Caffeine)
     *
     * 顺序很重要！如果先删 L1 再删 L2：
     *   线程A 删 L1 → 线程B 读 L1 miss → 线程B 读 L2 命中 → 线程B 回填 L1（旧值）
     *   → 线程A 删 L2 → 此时 L1 中是旧值，L2 已删，数据不一致！
     *
     * 先删 L2 再删 L1：
     *   线程A 删 L2 → 线程B 读 L1 miss → 线程B 读 L2 miss → 线程B 查 DB → 回填
     *   → 线程A 删 L1 → 即使线程B回填了旧值，L1 也会被清除，下次读取会拿到最新值
     *
     * Redis DELETE 带重试（指数退避：200ms → 600ms → 1800ms），应对网络抖动。
     * 所有重试均失败时抛出异常，由上层（CacheConsistencyManager）记录并进入补偿队列。
     *
     * 但更推荐「延迟双删」策略（见 CacheConsistencyManager），这里只做即时删除。
     */
    @Override
    public void evict(Object key) {
        String redisKey = buildRedisKey(key);

        // 先删 L2(Redis)，带重试
        boolean redisDeleted = evictFromRedisWithRetry(redisKey);

        // 无论 Redis 删除成功与否，始终清除 L1
        // L1 是本地的，删除不会有网络问题
        caffeineCache.invalidate(key);

        if (!redisDeleted) {
            log.warn("Cache evict L2 failed after retries, only L1 cleared | cache={}, key={}", name, key);
            // 抛出异常，让上层记录失败的 key 进行补偿
            throw new RuntimeException(
                    "Failed to evict Redis cache after " + EVICT_MAX_RETRIES + " retries: " + redisKey);
        }

        log.debug("Cache EVICT (L1+L2) | cache={}, key={}", name, key);
    }

    /**
     * 带指数退避重试的 Redis DELETE。
     * @return true if deleted successfully, false if all retries exhausted
     */
    private boolean evictFromRedisWithRetry(String redisKey) {
        for (int attempt = 1; attempt <= EVICT_MAX_RETRIES; attempt++) {
            try {
                redisTemplate.delete(redisKey);
                return true;
            } catch (Exception e) {
                if (attempt < EVICT_MAX_RETRIES) {
                    long delay = EVICT_RETRY_BASE_DELAY_MS * Math.round(Math.pow(3, attempt - 1));
                    log.warn("Cache evict L2 failed, retrying | cache={}, key={}, attempt={}/{}, delay={}ms, error={}",
                            name, redisKey, attempt, EVICT_MAX_RETRIES, delay, e.getMessage());
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                } else {
                    log.error("Cache evict L2 exhausted all retries | cache={}, key={}",
                            name, redisKey, e);
                }
            }
        }
        return false;
    }

    /**
     * 仅删除 Redis（L2），不清除本地 Caffeine（L1）。
     * 用于补偿重试场景 —— 本地 L1 可能已经过期或不在当前 JVM 实例中。
     */
    public boolean evictRedisOnly(Object key) {
        String redisKey = buildRedisKey(key);
        return evictFromRedisWithRetry(redisKey);
    }

    /**
     * 仅清除本地 Caffeine（L1），不操作 Redis（L2）。
     * 用于事务内即时清除 —— L1 是本地的，不存在网络问题。
     * L2 的删除延迟到事务 COMMIT 后执行。
     */
    public void evictCaffeineOnly(Object key) {
        caffeineCache.invalidate(key);
        log.debug("Cache EVICT L1 only | cache={}, key={}", name, key);
    }

    @Override
    public void clear() {
        var keys = redisTemplate.keys(redisKeyPrefix + name + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        caffeineCache.invalidateAll();
        log.debug("Cache CLEAR | cache={}", name);
    }

    private String buildRedisKey(Object key) {
        return redisKeyPrefix + name + ":" + key;
    }
}
