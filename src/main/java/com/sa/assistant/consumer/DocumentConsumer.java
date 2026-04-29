package com.sa.assistant.consumer;

import com.rabbitmq.client.Channel;
import com.sa.assistant.common.exception.NonRetryableDocumentException;
import com.sa.assistant.config.RabbitMQConfig;
import com.sa.assistant.model.dto.DocumentTaskMessage;
import com.sa.assistant.model.dto.TextChunk;
import com.sa.assistant.service.DocumentParseService;
import com.sa.assistant.service.DocumentService;
import com.sa.assistant.service.DocumentTaskProgressManager;
import com.sa.assistant.service.TextChunkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文档处理消费者。
 *
 * <h3>完整处理流程</h3>
 * <pre>
 * Tika解析(带5min超时+10MB限制) → 文本切块 → Embed向量化 → 写入ChromaDB → 更新DB/Redis状态
 * </pre>
 *
 * <h3>重试策略</h3>
 * 使用 MANUAL ack + NACK(requeue) 实现原地重试：
 * 最多重试 3 次，每次 re-queue 后 RabbitMQ 标记 redelivered=true。
 * 重试耗尽 → NACK(requeue=false) → 进入 DLX。
 *
 * <h3>非瞬态错误</h3>
 * {@link NonRetryableDocumentException} 直接 NACK → DLX，不浪费重试机会。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentConsumer {

    private final DocumentParseService parseService;
    private final TextChunkService chunkService;
    private final DocumentTaskProgressManager progressManager;
    private final DocumentService documentService;

    private final Map<String, Integer> retryCounters = new ConcurrentHashMap<>();

    @RabbitListener(
            queues = RabbitMQConfig.DOCUMENT_QUEUE,
            containerFactory = "documentListenerContainerFactory"
    )
    public void onDocumentTask(DocumentTaskMessage taskMessage,
                               Channel channel,
                               Message message,
                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        String taskId = taskMessage.getTaskId();
        boolean isRedelivered = message.getMessageProperties().isRedelivered();

        log.info("Received document task | taskId={}, documentId={}, fileName={}, redelivered={}",
                taskId, taskMessage.getDocumentId(), taskMessage.getFileName(), isRedelivered);

        try {
            processDocument(taskMessage);

            channel.basicAck(deliveryTag, false);
            retryCounters.remove(taskId);
            log.info("Document task ACKed | taskId={}", taskId);

        } catch (Throwable t) {
            log.error("Document task failed | taskId={}, errorType={}, error={}",
                    taskId, t.getClass().getSimpleName(), t.getMessage(), t);
            handleFailure(taskMessage, channel, deliveryTag, t);
        }
    }

    private void processDocument(DocumentTaskMessage taskMessage) {
        String taskId = taskMessage.getTaskId();
        Long documentId = taskMessage.getDocumentId();

        progressManager.updateProgress(taskId, 10, "开始解析文档...");

        String content = parseService.parseDocument(taskMessage.getFilePath());
        progressManager.updateProgress(taskId, 40, "文档解析完成，开始文本切块...");

        List<TextChunk> chunks = chunkService.chunkText(content, documentId);
        progressManager.updateProgress(taskId, 60, "文本切块完成，共 " + chunks.size() + " 个分块，准备向量化...");

        chunkService.embedAndStore(chunks, documentId);
        progressManager.updateProgress(taskId, 85, "向量化入库完成，更新数据库状态...");

        documentService.completeDocument(documentId, chunks.size());
        progressManager.completeTask(taskId, chunks.size());
    }

    // ---- 失败处理与重试策略 ----

    private void handleFailure(DocumentTaskMessage taskMessage, Channel channel,
                               long deliveryTag, Throwable t) {
        String taskId = taskMessage.getTaskId();

        try {
            if (isNonRetryableError(t)) {
                log.warn("Non-retryable error, NACK → DLX | taskId={}, error={}",
                        taskId, t.getMessage());
                failAndGoToDlx(taskMessage, channel, deliveryTag, t);
                return;
            }

            int currentRetries = retryCounters.merge(taskId, 1, Integer::sum);

            if (currentRetries <= RabbitMQConfig.MAX_RETRY_COUNT) {
                log.info("Retrying | taskId={}, attempt={}/{}, error={}",
                        taskId, currentRetries, RabbitMQConfig.MAX_RETRY_COUNT, t.getMessage());
                channel.basicNack(deliveryTag, false, true);
            } else {
                log.error("Retries exhausted | taskId={}, maxRetries={}",
                        taskId, RabbitMQConfig.MAX_RETRY_COUNT);
                failAndGoToDlx(taskMessage, channel, deliveryTag,
                        new RuntimeException("重试 " + RabbitMQConfig.MAX_RETRY_COUNT + " 次后仍失败: " + t.getMessage(), t));
            }

        } catch (Exception ex) {
            log.error("handleFailure itself failed | taskId={}", taskId, ex);
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (Exception ignored) {
            }
        }
    }

    private void failAndGoToDlx(DocumentTaskMessage taskMessage, Channel channel,
                                long deliveryTag, Throwable t) {
        String taskId = taskMessage.getTaskId();

        progressManager.failTask(taskId, t.getMessage());
        documentService.markFailed(taskMessage.getDocumentId());
        retryCounters.remove(taskId);

        try {
            channel.basicNack(deliveryTag, false, false);
        } catch (Exception nackEx) {
            log.error("NACK failed | taskId={}", taskId, nackEx);
        }
    }

    /**
     * 判断是否为不可重试的错误。
     * 使用异常类型而非字符串匹配 — 结构性错误不应重试。
     */
    private boolean isNonRetryableError(Throwable t) {
        if (t instanceof NonRetryableDocumentException) {
            return true;
        }
        Throwable cause = t.getCause();
        return cause instanceof NonRetryableDocumentException;
    }
}
