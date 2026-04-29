package com.sa.assistant.service;

import com.sa.assistant.model.dto.DocumentStatus;
import com.sa.assistant.model.dto.DocumentTaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentTaskProgressManager {

    private static final String TASK_KEY_PREFIX = "sa:task:doc:";
    private static final long TASK_TTL_HOURS = 24;

    private final RedisTemplate<String, Object> redisTemplate;

    public void initTask(String taskId, Long documentId, String fileName) {
        DocumentTaskStatus status = DocumentTaskStatus.builder()
                .taskId(taskId)
                .documentId(documentId)
                .status(DocumentStatus.PROCESSING.name())
                .progress(0)
                .message("任务已创建，等待处理: " + fileName)
                .build();
        save(taskId, status);
        log.info("Task initialized | taskId={}, documentId={}, fileName={}", taskId, documentId, fileName);
    }

    public void updateProgress(String taskId, int progress, String message) {
        DocumentTaskStatus status = get(taskId);
        if (status != null) {
            status.setProgress(progress);
            status.setMessage(message);
            save(taskId, status);
            log.debug("Task progress updated | taskId={}, progress={}%, message={}", taskId, progress, message);
        }
    }

    public void completeTask(String taskId, int chunkCount) {
        DocumentTaskStatus status = get(taskId);
        if (status != null) {
            status.setStatus(DocumentStatus.COMPLETED.name());
            status.setProgress(100);
            status.setMessage("文档处理完成");
            status.setChunkCount(chunkCount);
            save(taskId, status);
            log.info("Task completed | taskId={}, chunkCount={}", taskId, chunkCount);
        }
    }

    public void failTask(String taskId, String errorMessage) {
        DocumentTaskStatus status = get(taskId);
        if (status != null) {
            status.setStatus(DocumentStatus.FAILED.name());
            status.setMessage("文档处理失败");
            status.setErrorMessage(errorMessage);
            save(taskId, status);
            log.error("Task failed | taskId={}, error={}", taskId, errorMessage);
        }
    }

    public DocumentTaskStatus get(String taskId) {
        String key = TASK_KEY_PREFIX + taskId;
        Object obj = redisTemplate.opsForValue().get(key);
        if (obj instanceof DocumentTaskStatus) {
            return (DocumentTaskStatus) obj;
        }
        return null;
    }

    private void save(String taskId, DocumentTaskStatus status) {
        String key = TASK_KEY_PREFIX + taskId;
        redisTemplate.opsForValue().set(key, status, TASK_TTL_HOURS, TimeUnit.HOURS);
    }
}
