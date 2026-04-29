package com.sa.assistant.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sa.assistant.config.RabbitMQConfig;
import com.sa.assistant.model.dto.DocumentTaskMessage;
import com.sa.assistant.service.DocumentService;
import com.sa.assistant.service.DocumentTaskProgressManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.utils.SerializationUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 死信消费者：处理所有重试耗尽后进入 DLX 的消息。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>解析原始 {@link DocumentTaskMessage}，提取 documentId 和 taskId</li>
 *   <li>通过 {@link DocumentService} 更新文档状态为 DEAD</li>
 *   <li>通过 {@link DocumentTaskProgressManager} 更新任务状态为 FAILED</li>
 *   <li>记录完整死信日志，供排查和告警</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterConsumer {

    private final DocumentService documentService;
    private final DocumentTaskProgressManager progressManager;
    private final ObjectMapper objectMapper;

    @RabbitListener(
            queues = RabbitMQConfig.DLX_QUEUE,
            containerFactory = "documentListenerContainerFactory"
    )
    public void onDeadLetter(org.springframework.amqp.core.Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        Map<String, Object> headers = message.getMessageProperties().getHeaders();

        String originalQueue = extractOriginalQueue(headers);
        String deathReason = extractDeathReason(headers);

        log.error("Dead letter received | messageId={}, originalQueue={}, reason={}, deathInfo={}",
                messageId, originalQueue, deathReason, headers.get("x-death"));

        DocumentTaskMessage taskMessage = deserializeTaskMessage(message);

        if (taskMessage != null && taskMessage.getTaskId() != null) {
            Long documentId = taskMessage.getDocumentId();
            String taskId = taskMessage.getTaskId();

            String failReason = "文档处理最终失败（死信队列）: " + (deathReason != null ? deathReason : "未知原因");
            progressManager.failTask(taskId, failReason);

            if (documentId != null) {
                documentService.markDead(documentId, deathReason);
            }

            log.error("Dead letter processed | taskId={}, documentId={}, fileName={}",
                    taskId, documentId, taskMessage.getFileName());
        } else {
            String body = new String(message.getBody());
            log.error("Dead letter (unparseable) | messageId={}, bodyPreview={}",
                    messageId, body.length() > 500 ? body.substring(0, 500) + "..." : body);
        }
    }

    private DocumentTaskMessage deserializeTaskMessage(org.springframework.amqp.core.Message message) {
        try {
            return objectMapper.readValue(message.getBody(), DocumentTaskMessage.class);
        } catch (Exception e) {
            log.debug("JSON deserialization failed for dead letter, trying Java serialization");
        }

        try {
            Object obj = SerializationUtils.deserialize(message.getBody());
            if (obj instanceof DocumentTaskMessage) {
                return (DocumentTaskMessage) obj;
            }
        } catch (Exception e) {
            log.debug("Java deserialization also failed for dead letter");
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractOriginalQueue(Map<String, Object> headers) {
        try {
            Object xDeath = headers.get("x-death");
            if (xDeath instanceof List && !((List<?>) xDeath).isEmpty()) {
                Object firstDeath = ((List<?>) xDeath).get(0);
                if (firstDeath instanceof Map) {
                    Object queue = ((Map<String, Object>) firstDeath).get("queue");
                    return queue != null ? queue.toString() : "unknown";
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract original queue from x-death header", e);
        }
        return "unknown";
    }

    @SuppressWarnings("unchecked")
    private String extractDeathReason(Map<String, Object> headers) {
        try {
            Object xDeath = headers.get("x-death");
            if (xDeath instanceof List && !((List<?>) xDeath).isEmpty()) {
                Object firstDeath = ((List<?>) xDeath).get(0);
                if (firstDeath instanceof Map) {
                    Object reason = ((Map<String, Object>) firstDeath).get("reason");
                    return reason != null ? reason.toString() : "rejected";
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract death reason from x-death header", e);
        }
        return "rejected";
    }
}
