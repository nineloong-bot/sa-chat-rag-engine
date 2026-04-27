package com.sa.assistant.repository;

import com.sa.assistant.model.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {

    List<DocumentEntity> findByStatusOrderByCreatedAtDesc(String status);
}
