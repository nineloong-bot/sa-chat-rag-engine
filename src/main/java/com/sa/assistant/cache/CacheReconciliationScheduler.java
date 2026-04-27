package com.sa.assistant.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 缓存一致性补偿任务。
 *
 * <h3>职责</h3>
 * 定时扫描 {@code sa:cache:evict:pending:{cacheName}} Redis Set，
 * 对之前因网络故障导致 L2 删除失败的缓存 key 进行重试驱逐。
 *
 * <h3>调度频率</h3>
 * <ul>
 *   <li>轻量扫描（每 10 秒）：耗时的瞬时失败（TTL 窗口内可容忍 10s 延迟）</li>
 *   <li>全量扫描（每 60 秒）：兜底耗时的持续网络故障</li>
 * </ul>
 *
 * <h3>监控指标</h3>
 * 通过 {@link CacheConsistencyManager#getPendingEvictionCount(String)} 暴露待处理数量，
 * 可接入 Prometheus / 告警系统。当队列持续增长时说明 Redis 或网络异常。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheReconciliationScheduler {

    private final CacheConsistencyManager consistencyManager;

    /**
     * 快速扫描：每 10 秒执行一次，处理聊谈记录的瞬时逐出失败。
     * 10 秒的窗口在 20 分钟 TTL 面前可以忽略不计。
     */
    @Scheduled(fixedRate = 10_000)
    public void fastReconcileChatHistory() {
        try {
            consistencyManager.retryFailedEvictions("chatHistory");
        } catch (Exception e) {
            log.error("Fast reconciliation failed for chatHistory", e);
        }
    }

    /**
     * 全量扫描：每 60 秒执行一次，覆盖所有缓存名称的补偿。
     * 包括 chatHistory 和未来可能增加的其他缓存。
     */
    @Scheduled(fixedRate = 60_000)
    public void fullReconcile() {
        try {
            boolean chatHistoryClean = consistencyManager.retryFailedEvictions("chatHistory");
            long pending = consistencyManager.getPendingEvictionCount("chatHistory");

            if (!chatHistoryClean || pending > 0) {
                log.warn("Cache reconciliation incomplete | cache=chatHistory, pending={}", pending);
            }
        } catch (Exception e) {
            log.error("Full reconciliation failed", e);
        }
    }
}
