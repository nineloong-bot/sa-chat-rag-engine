package com.sa.assistant.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 缓存一致性管理器。
 *
 * <h3>三层兜底策略</h3>
 * <ol>
 *   <li><b>即时删除 L1(Caffeine)</b>：无网络风险，事务内直接执行</li>
 *   <li><b>尝试删除 L2(Redis)</b>：带 3 次指数退避重试，事务内直接执行</li>
 *   <li><b>失败补偿</b>：重试耗尽 → 记录到 Redis Set → {@link CacheReconciliationScheduler} 定时重试</li>
 *   <li><b>TTL 兜底</b>：Redis TTL（20min）确保脏数据最终消亡</li>
 * </ol>
 *
 * <h3>为什么不在事务 COMMIT 后才删缓存？</h3>
 *
 * 理想顺序是"COMMIT → 删缓存"，但这有两种实现方式：
 *   a) @TransactionalEventListener —— 强依赖 Spring 事件总线，在 @Async 等方法中不可靠
 *   b) 消息队列 —— 引入额外复杂度
 * 折中方案：在事务内执行 L1+L2 删除 + 3 次重试。如果 Redis 删除失败：
 *   - L1 已被清除（无网络风险），当前 JVM 实例不会读到旧缓存
 *   - L2 删除失败 key 记录到 Redis Set，由补偿任务重试
 *   - 最坏情况：TTL 过期（20min）后一致性自然恢复
 */
@Slf4j
@Component
public class CacheConsistencyManager {

    private final MultiLevelCacheManager multiLevelCacheManager;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Redis Set key 前缀：记录驱逐失败待补偿的缓存 key。
     * 完整格式：sa:cache:evict:pending:{cacheName}
     */
    static final String EVICT_PENDING_KEY_PREFIX = "sa:cache:evict:pending:";

    public CacheConsistencyManager(MultiLevelCacheManager multiLevelCacheManager,
                                   @Qualifier("redisTemplate") RedisTemplate<String, Object> redisTemplate) {
        this.multiLevelCacheManager = multiLevelCacheManager;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 更新 DB 后驱逐缓存（Cache-Aside write invalidation）。
     *
     * <h3>执行步骤</h3>
     * <pre>
     *   1. 获取 MultiLevelCache 实例
     *   2. 清除 L1(Caffeine) —— 无网络风险，必然成功
     *   3. 尝试删除 L2(Redis) —— 带 3 次重试
     *   4. 如果 L2 删除失败 → 记录到 Redis Set → 补偿任务重试
     * </pre>
     *
     * @param cacheName 缓存名称（如 "chatHistory"）
     * @param key       缓存 key
     */
    public void evictAfterUpdate(String cacheName, Object key) {
        org.springframework.cache.Cache cache = multiLevelCacheManager.getCache(cacheName);
        if (!(cache instanceof MultiLevelCache multiLevelCache)) {
            log.warn("Not a MultiLevelCache | cache={}, type={}", cacheName,
                    cache != null ? cache.getClass().getSimpleName() : "null");
            return;
        }

        // Step 1: 清除 L1(Caffeine) —— 本地的，无网络风险
        multiLevelCache.evictCaffeineOnly(key);

        // Step 2: 删除 L2(Redis) —— 带 3 次指数退避重试
        boolean deleted = multiLevelCache.evictRedisOnly(key);

        if (!deleted) {
            // Step 3: 重试耗尽 → 记录到 Redis Set，由补偿任务处理
            log.warn("L2 evict failed, recording for reconciliation | cache={}, key={}", cacheName, key);
            recordPendingEviction(cacheName, key);
        } else {
            // 删除成功 → 清理之前可能遗留的失败记录
            removePendingEviction(cacheName, key);
        }
    }

    /**
     * 重试所有待处理的失败驱逐记录（供 {@link CacheReconciliationScheduler} 调用）。
     *
     * @param cacheName 缓存名称
     * @return true if all pending evictions succeeded, false if some still remain
     */
    public boolean retryFailedEvictions(String cacheName) {
        String pendingKey = EVICT_PENDING_KEY_PREFIX + cacheName;
        org.springframework.cache.Cache cache = multiLevelCacheManager.getCache(cacheName);
        if (!(cache instanceof MultiLevelCache multiLevelCache)) {
            return true;
        }

        try {
            Long size = redisTemplate.opsForSet().size(pendingKey);
            if (size == null || size == 0) {
                return true;
            }

            log.debug("Reconciling pending cache evictions | cache={}, pending={}", cacheName, size);

            boolean allSucceeded = true;
            int batchSize = Math.min(size.intValue(), 100); // 每次最多处理 100 条

            for (int i = 0; i < batchSize; i++) {
                Object keyObj = redisTemplate.opsForSet().pop(pendingKey);
                if (keyObj == null) break;

                String key = keyObj.toString();
                boolean deleted = multiLevelCache.evictRedisOnly(key);

                if (!deleted) {
                    // 仍失败 → 放回去，下次再试
                    redisTemplate.opsForSet().add(pendingKey, key);
                    redisTemplate.expire(pendingKey, 2, TimeUnit.HOURS);
                    allSucceeded = false;
                }
            }

            return allSucceeded;
        } catch (Exception e) {
            log.error("Reconciliation failed | cache={}", cacheName, e);
            return false;
        }
    }

    /**
     * 获取当前待补偿的驱逐数量（监控用）。
     */
    public long getPendingEvictionCount(String cacheName) {
        try {
            Long size = redisTemplate.opsForSet().size(EVICT_PENDING_KEY_PREFIX + cacheName);
            return size != null ? size : 0;
        } catch (Exception e) {
            log.warn("Failed to get pending eviction count | cache={}", cacheName, e);
            return -1;
        }
    }

    // ---- 内部方法 ----

    private void recordPendingEviction(String cacheName, Object key) {
        try {
            String pendingKey = EVICT_PENDING_KEY_PREFIX + cacheName;
            redisTemplate.opsForSet().add(pendingKey, key.toString());
            // 保留 2 小时，足够补偿任务处理；超时后 TTL 兜底
            redisTemplate.expire(pendingKey, 2, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("Failed to record pending eviction | cache={}, key={}", cacheName, key, e);
        }
    }

    private void removePendingEviction(String cacheName, Object key) {
        try {
            redisTemplate.opsForSet().remove(EVICT_PENDING_KEY_PREFIX + cacheName, key.toString());
        } catch (Exception e) {
            log.debug("Failed to remove pending eviction record | cache={}, key={}", cacheName, key);
        }
    }
}
