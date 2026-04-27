package com.sa.assistant.repository;

import com.sa.assistant.model.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findBySessionIdOrderByCreatedAtDesc(String sessionId);
}
