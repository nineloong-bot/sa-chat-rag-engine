package com.sa.assistant.controller;

import com.sa.assistant.common.result.R;
import com.sa.assistant.model.dto.DocumentTaskStatus;
import com.sa.assistant.model.entity.DocumentEntity;
import com.sa.assistant.repository.DocumentRepository;
import com.sa.assistant.service.DocumentTaskProgressManager;
import com.sa.assistant.service.DocumentUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentUploadService uploadService;
    private final DocumentTaskProgressManager progressManager;
    private final DocumentRepository documentRepository;

    @GetMapping
    public R<List<DocumentEntity>> listCompleted() {
        List<DocumentEntity> documents = documentRepository.findByStatusOrderByCreatedAtDesc("COMPLETED");
        return R.ok(documents);
    }

    @PostMapping("/upload")
    public R<DocumentTaskStatus> upload(@RequestParam("file") MultipartFile file) {
        DocumentTaskStatus status = uploadService.upload(file);
        return R.ok(status);
    }

    @GetMapping("/task/{taskId}")
    public R<DocumentTaskStatus> getTaskStatus(@PathVariable String taskId) {
        DocumentTaskStatus status = progressManager.get(taskId);
        if (status == null) {
            return R.fail(404, "任务不存在: " + taskId);
        }
        return R.ok(status);
    }
}
