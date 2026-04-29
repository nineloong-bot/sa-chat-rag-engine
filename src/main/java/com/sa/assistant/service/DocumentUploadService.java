package com.sa.assistant.service;

import com.sa.assistant.common.exception.BusinessException;
import com.sa.assistant.config.RabbitMQConfig;
import com.sa.assistant.model.dto.DocumentStatus;
import com.sa.assistant.model.dto.DocumentTaskMessage;
import com.sa.assistant.model.dto.DocumentTaskStatus;
import com.sa.assistant.model.entity.DocumentEntity;
import com.sa.assistant.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentUploadService {

    private final DocumentRepository documentRepository;
    private final RabbitTemplate rabbitTemplate;
    private final DocumentTaskProgressManager progressManager;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".pdf", ".docx", ".doc", ".txt", ".md");

    @Value("${app.upload.dir:/tmp/sa-uploads}")
    private String uploadDir;

    @Transactional
    public DocumentTaskStatus upload(MultipartFile file) {
        validateFile(file);

        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String storedFileName = UUID.randomUUID() + extension;
        Path filePath = saveFile(file, storedFileName);

        DocumentEntity document = DocumentEntity.builder()
                .fileName(originalFilename)
                .fileType(extension.replace(".", "").toUpperCase())
                .fileSize(file.getSize())
                .filePath(filePath.toString())
                .status(DocumentStatus.PENDING.name())
                .build();
        document = documentRepository.save(document);

        String taskId = UUID.randomUUID().toString();
        progressManager.initTask(taskId, document.getId(), originalFilename);

        document.setStatus(DocumentStatus.QUEUED.name());
        documentRepository.save(document);

        DocumentTaskMessage message = DocumentTaskMessage.builder()
                .documentId(document.getId())
                .taskId(taskId)
                .fileName(originalFilename)
                .filePath(filePath.toString())
                .fileType(extension.replace(".", "").toUpperCase())
                .build();

        CorrelationData correlationData = new CorrelationData(taskId);
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.RAG_EXCHANGE,
                RabbitMQConfig.DOCUMENT_ROUTING_KEY,
                message,
                correlationData
        );

        log.info("Document uploaded & queued | documentId={}, taskId={}, fileName={}",
                document.getId(), taskId, originalFilename);

        return progressManager.get(taskId);
    }

    /**
     * 校验上传文件。
     * 仅通过扩展名判断类型，contentType 可被伪造，不可信赖。
     */
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(400, "上传文件不能为空");
        }

        String extension = getFileExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(400, "不支持的文件扩展名: " + extension + "，仅支持 .pdf/.docx/.doc/.txt/.md");
        }
    }

    private Path saveFile(MultipartFile file, String storedFileName) {
        try {
            Path dir = Paths.get(uploadDir);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            Path filePath = dir.resolve(storedFileName);
            Files.copy(file.getInputStream(), filePath);
            log.debug("File saved | path={}", filePath);
            return filePath;
        } catch (IOException e) {
            throw new BusinessException("文件保存失败: " + e.getMessage());
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".")).toLowerCase();
    }
}
