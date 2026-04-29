package com.sa.assistant.service;

import com.sa.assistant.model.dto.DocumentStatus;
import com.sa.assistant.model.entity.DocumentEntity;
import com.sa.assistant.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文档状态管理服务。
 * 统一管理文档状态流转，Consumer 和其他服务不再直接操作 Repository。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;

    @Transactional
    public void updateStatus(Long documentId, DocumentStatus status) {
        DocumentEntity doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("文档不存在: " + documentId));
        doc.setStatus(status.name());
        documentRepository.save(doc);
        log.info("Document status updated | documentId={}, status={}", documentId, status);
    }

    @Transactional
    public void completeDocument(Long documentId, int chunkCount) {
        DocumentEntity doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("文档不存在: " + documentId));
        doc.setStatus(DocumentStatus.COMPLETED.name());
        doc.setChunkCount(chunkCount);
        documentRepository.save(doc);
        log.info("Document completed | documentId={}, chunkCount={}", documentId, chunkCount);
    }

    @Transactional
    public void markFailed(Long documentId) {
        try {
            DocumentEntity doc = documentRepository.findById(documentId).orElse(null);
            if (doc != null) {
                doc.setStatus(DocumentStatus.FAILED.name());
                documentRepository.save(doc);
                log.info("Document marked as FAILED | documentId={}", documentId);
            } else {
                log.warn("Document not found for FAILED status update | documentId={}", documentId);
            }
        } catch (Exception e) {
            log.error("Failed to update document FAILED status | documentId={}", documentId, e);
        }
    }

    @Transactional
    public void markDead(Long documentId, String reason) {
        try {
            DocumentEntity doc = documentRepository.findById(documentId).orElse(null);
            if (doc != null) {
                doc.setStatus(DocumentStatus.DEAD.name());
                documentRepository.save(doc);
                log.info("Document marked as DEAD | documentId={}, reason={}", documentId, reason);
            } else {
                log.warn("Document not found for DEAD status update | documentId={}", documentId);
            }
        } catch (Exception e) {
            log.error("Failed to update document DEAD status | documentId={}", documentId, e);
        }
    }
}
