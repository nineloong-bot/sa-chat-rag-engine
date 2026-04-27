package com.sa.assistant.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sa.assistant.config.RabbitMQConfig;
import com.sa.assistant.model.dto.DocumentTaskMessage;
import com.sa.assistant.model.entity.DocumentEntity;
import com.sa.assistant.repository.DocumentRepository;
import com.sa.assistant.service.DocumentTaskProgressManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.utils.SerializationUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 死信消费者：处理所有重试耗尽后进入 DLX 的消息。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>解析原始 {@link DocumentTaskMessage}，提取 documentId 和 taskId</li>
 *   <li>更新 {@code DocumentEntity.status = "DEAD"}，记录死信原因</li>
 *   <li>更新 {@code DocumentTaskProgressManager} 任务状态 = FAILED</li>
 *   <li>记录完整死信日志，供排查和告警</li>
 *   <li>TODO: 集成钉钉/企微/邮件告警通知</li>
 *   <li>TODO: 写入 dead_letter_record 表，提供管理后台手动重试入口</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterConsumer {

    private final DocumentRepository documentRepository;
    private final DocumentTaskProgressManager progressManager;
    private final ObjectMapper objectMapper;

    @RabbitListener(
            queues = RabbitMQConfig.DLX_QUEUE,
            containerFactory = "documentListenerContainerFactory"
    )
    public void onDeadLetter(org.springframework.amqp.core.Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        Map<String, Object> headers = message.getMessageProperties().getHeaders();

        // 提取死信元信息
        String originalQueue = extractOriginalQueue(headers);
        String deathReason = extractDeathReason(headers);

        log.error("Dead letter received | messageId={}, originalQueue={}, reason={}, deathInfo={}",
                messageId, originalQueue, deathReason, headers.get("x-death"));

        // 尝试反序列化原始消息体
        DocumentTaskMessage taskMessage = deserializeTaskMessage(message);

        if (taskMessage != null && taskMessage.getTaskId() != null) {
            Long documentId = taskMessage.getDocumentId();
            String taskId = taskMessage.getTaskId();

            // 1. 更新 Redis 任务状态（前端轮询可见）
            progressManager.failTask(taskId,
                    "文档处理最终失败（死信队列）: " + (deathReason != null ? deathReason : "未知原因"));

            // 2. 更新 DB 文档状态
            if (documentId != null) {
                updateDocumentToDead(documentId, deathReason);
            }

            log.error("Dead letter processed | taskId={}, documentId={}, fileName={}",
                    taskId, documentId, taskMessage.getFileName());
        } else {
            // 无法反序列化 — 记录原始 body
            String body = new String(message.getBody());
            log.error("Dead letter (unparseable) | messageId={}, bodyPreview={}",
                    messageId, body.length() > 500 ? body.substring(0, 500) + "..." : body);
        }

        // TODO: 集成告警通知
        // alertService.send("文档处理进入死信队列", taskMessage);
        // TODO: 写入 dead_letter_record 表，提供管理后台手动重试入口
        // deadLetterRecordRepository.save(new DeadLetterRecord(...));
    }

    /**
     * 尝试从消息体反序列化 {@link DocumentTaskMessage}。
     * 首先尝试 JSON 反序列化，失败则尝试 Java 序列化。
     */
    private DocumentTaskMessage deserializeTaskMessage(org.springframework.amqp.core.Message message) {
        // 尝试 1：JSON 反序列化（Jackson2JsonMessageConverter 编码）
        try {
            return objectMapper.readValue(message.getBody(), DocumentTaskMessage.class);
        } catch (Exception e) {
            log.debug("JSON deserialization failed for dead letter, trying Java serialization");
        }

        // 尝试 2：Java 序列化
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

    /**
     * 从 x-death header 提取原始队列名称。
     */
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

    /**
     * 从 x-death header 提取死信原因（rejected/expired/maxlen）。
     */
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

    /**
     * 更新 DB 中文档状态为 DEAD。
     */
    @Transactional
    public void updateDocumentToDead(Long documentId, String reason) {
        try {
            DocumentEntity doc = documentRepository.findById(documentId).orElse(null);
            if (doc != null) {
                doc.setStatus("DEAD");
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
