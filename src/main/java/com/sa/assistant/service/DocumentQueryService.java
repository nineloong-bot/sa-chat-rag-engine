package com.sa.assistant.service;

import com.sa.assistant.model.dto.DocumentStatus;
import com.sa.assistant.model.entity.DocumentEntity;
import com.sa.assistant.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文档查询服务。
 * 将 DocumentController 中直接访问 Repository 的逻辑抽到 Service 层。
 */
@Service
@RequiredArgsConstructor
public class DocumentQueryService {

    private final DocumentRepository documentRepository;

    public List<DocumentEntity> listCompleted() {
        return documentRepository.findByStatusOrderByCreatedAtDesc(DocumentStatus.COMPLETED.name());
    }

    public List<DocumentEntity> listAll() {
        return documentRepository.findAll();
    }
}
