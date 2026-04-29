package com.sa.assistant.repository;

import com.sa.assistant.model.entity.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {

    List<ChatHistory> findBySessionIdAndDeletedFalseOrderByCreatedAtAsc(String sessionId);

    Optional<ChatHistory> findByIdAndDeletedFalse(Long id);

    long countBySessionIdAndDeletedFalse(String sessionId);
}
