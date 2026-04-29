package com.sa.assistant.repository;

import com.sa.assistant.model.entity.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {

    List<ChatHistory> findBySessionIdAndDeletedFalseOrderByCreatedAtAsc(String sessionId);

    Optional<ChatHistory> findByIdAndDeletedFalse(Long id);

    long countBySessionIdAndDeletedFalse(String sessionId);

    /**
     * 查询所有不重复的 sessionId（仅包含未删除的记录）
     */
    @Query("SELECT DISTINCT h.sessionId FROM ChatHistory h WHERE h.deleted = false")
    List<String> findDistinctSessionIds();

    /**
     * 按 sessionId 分组统计消息数、最后更新时间
     */
    @Query("SELECT h.sessionId, COUNT(h), MAX(h.createdAt) FROM ChatHistory h WHERE h.deleted = false GROUP BY h.sessionId ORDER BY MAX(h.createdAt) DESC")
    List<Object[]> findSessionSummaries();
}
