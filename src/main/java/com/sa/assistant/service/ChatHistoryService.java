package com.sa.assistant.service;

import com.sa.assistant.cache.CacheConsistencyManager;
import com.sa.assistant.common.exception.BusinessException;
import com.sa.assistant.common.lock.DistributedLock;
import com.sa.assistant.model.dto.ChatHistoryCreateDTO;
import com.sa.assistant.model.dto.ChatHistoryUpdateDTO;
import com.sa.assistant.model.entity.ChatHistory;
import com.sa.assistant.repository.ChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    private static final String CACHE_NAME = "chatHistory";
    private static final String LOCK_PREFIX = "lock:chat:";

    private static final long LOCK_EXPIRE_SECONDS = 10;

    private final ChatHistoryRepository chatHistoryRepository;
    private final CacheConsistencyManager cacheConsistencyManager;
    private final DistributedLock distributedLock;

    /*
     * 创建对话记录
     *
     * 写操作不走缓存，直接写DB。写入后不需要主动填充缓存——
     * 下次读取时自然会通过 cache miss 触发懒加载。
     * 这就是 Cache-Aside 模式的核心思想：「按需加载」。
     *
     * 使用 WatchDog 续期锁：即使 DB 写入耗时超过 LOCK_EXPIRE_SECONDS，
     * WatchDog 也会自动续期，保证互斥不失效。
     */
    @Transactional
    public ChatHistory create(ChatHistoryCreateDTO dto) {
        String lockKey = LOCK_PREFIX + "create:" + dto.getSessionId();
        return distributedLock.executeWithLockWatchDog(lockKey, LOCK_EXPIRE_SECONDS, () -> {
            ChatHistory entity = new ChatHistory();
            entity.setSessionId(dto.getSessionId());
            entity.setRole(dto.getRole());
            entity.setContent(dto.getContent());
            entity.setModel(dto.getModel());
            entity.setTokenUsage(dto.getTokenUsage());
            entity.setMetadata(dto.getMetadata());
            entity.setIsDeleted(false);

            ChatHistory saved = chatHistoryRepository.save(entity);
            log.info("ChatHistory created | id={}, session={}", saved.getId(), saved.getSessionId());
            return saved;
        });
    }

    /*
     * 按 ID 查询单条记录 —— 多级缓存核心读取路径
     *
     * @Cacheable 触发流程：
     *   1. Spring 调用 MultiLevelCacheManager.getCache("chatHistory")
     *   2. MultiLevelCache.lookup() 执行 L1(Caffeine) → L2(Redis) 查询
     *   3. 如果都 miss，执行本方法体查 DB
     *   4. 返回值自动写入 L1 + L2
     *
     * key = "#id" 表示用方法参数 id 作为缓存 key
     * unless = "#result == null" 表示空值不缓存
     *   —— 为什么不缓存 null？防止缓存穿透（Cache Penetration）
     *      如果大量请求查询不存在的 ID，缓存 null 会导致 Redis 充满无效条目
     *      更好的方案是 Bloom Filter，但当前场景下 ID 由 DB 自增，不存在非法 ID
     */
    @Cacheable(cacheManager = "multiLevelCacheManager", value = CACHE_NAME, key = "#id", unless = "#result == null")
    public ChatHistory getById(Long id) {
        log.debug("ChatHistory DB query | id={}", id);
        return chatHistoryRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(404, "对话记录不存在: " + id));
    }

    /*
     * 按 sessionId 查询会话下的所有对话记录
     *
     * 注意：列表查询通常不适合 @Cacheable，原因：
     *   1. 列表可能很长，占用大量缓存空间
     *   2. 列表内容随新消息加入频繁变化，缓存命中率低
     *   3. 分页参数组合导致缓存 key 爆炸
     *
     * 但对于「热点会话的最近消息」这种高频读取场景，缓存列表是值得的。
     * 这里简化处理，直接查 DB。后续可引入列表缓存 + 增量更新策略。
     */
    public List<ChatHistory> listBySessionId(String sessionId) {
        return chatHistoryRepository.findBySessionIdAndIsDeletedFalseOrderByCreatedAtAsc(sessionId);
    }

    /*
     * 更新对话记录 —— 缓存一致性核心路径
     *
     * 操作顺序：更新 DB → 删除缓存（Cache-Aside Pattern）
     *
     * 为什么不用 @CacheEvict？
     *   @CacheEvict 只做即时删除，无法实现延迟双删。
     *   我们手动调用 CacheConsistencyManager.evictAfterUpdate()，
     *   它会先即时删除，再异步延迟 500ms 二次删除，确保一致性。
     *
     * 为什么不用 @CachePut？
     *   @CachePut 会在方法执行后用返回值更新缓存。
     *   但在并发场景下，更新缓存比删除缓存更容易产生数据不一致：
     *   两个线程同时更新，后更新的可能把旧值写入缓存。
     */
    @Transactional
    public ChatHistory update(Long id, ChatHistoryUpdateDTO dto) {
        String lockKey = LOCK_PREFIX + "update:" + id;
        return distributedLock.executeWithLockWatchDog(lockKey, LOCK_EXPIRE_SECONDS, () -> {
            ChatHistory entity = chatHistoryRepository.findByIdAndIsDeletedFalse(id)
                    .orElseThrow(() -> new BusinessException(404, "对话记录不存在: " + id));

            entity.setContent(dto.getContent());
            if (dto.getModel() != null) {
                entity.setModel(dto.getModel());
            }
            if (dto.getTokenUsage() != null) {
                entity.setTokenUsage(dto.getTokenUsage());
            }
            if (dto.getMetadata() != null) {
                entity.setMetadata(dto.getMetadata());
            }

            ChatHistory updated = chatHistoryRepository.save(entity);

            cacheConsistencyManager.evictAfterUpdate(CACHE_NAME, id);

            log.info("ChatHistory updated | id={}", id);
            return updated;
        });
    }

    /*
     * 逻辑删除对话记录
     *
     * 使用逻辑删除而非物理删除，原因：
     *   1. 数据合规：对话记录可能需要审计追溯
     *   2. 缓存一致性：物理删除后，如果缓存未及时清除，
     *      读取到的是 null，而非「已删除」的明确状态
     *   3. 可恢复：误删可以恢复
     *
     * 逻辑删除后同样需要清除缓存，避免读到标记为已删除的数据。
     */
    @Transactional
    public void delete(Long id) {
        String lockKey = LOCK_PREFIX + "delete:" + id;
        distributedLock.executeWithLockWatchDog(lockKey, LOCK_EXPIRE_SECONDS, () -> {
            ChatHistory entity = chatHistoryRepository.findByIdAndIsDeletedFalse(id)
                    .orElseThrow(() -> new BusinessException(404, "对话记录不存在: " + id));

            entity.setIsDeleted(true);
            chatHistoryRepository.save(entity);

            cacheConsistencyManager.evictAfterUpdate(CACHE_NAME, id);

            log.info("ChatHistory logically deleted | id={}", id);
        });
    }
}
