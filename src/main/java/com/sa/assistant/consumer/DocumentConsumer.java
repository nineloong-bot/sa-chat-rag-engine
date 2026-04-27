package com.sa.assistant.consumer;

import com.rabbitmq.client.Channel;
import com.sa.assistant.config.RabbitMQConfig;
import com.sa.assistant.model.dto.DocumentTaskMessage;
import com.sa.assistant.model.dto.TextChunk;
import com.sa.assistant.model.entity.DocumentEntity;
import com.sa.assistant.repository.DocumentRepository;
import com.sa.assistant.service.DocumentParseService;
import com.sa.assistant.service.DocumentTaskProgressManager;
import com.sa.assistant.service.TextChunkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
 * 文件不存在、内容过大等直接 NACK → DLX，不浪费重试机会。
 *
 * <h3>OOM 处理</h3>
 * catch Throwable（覆盖 OutOfMemoryError），配合 Tika 的 10MB WriteOutContentHandler 限制。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentConsumer {

    private final DocumentParseService parseService;
    private final TextChunkService chunkService;
    private final DocumentTaskProgressManager progressManager;
    private final DocumentRepository documentRepository;

    /**
     * 重试计数器：taskId → 已重试次数。
     * 使用 ConcurrentHashMap，JVM 生命周期内有效。
     * 消费者重启后计数器丢失 → 消息最多被多处理一次，可接受。
     */
    private final Map<String, Integer> retryCounters = new ConcurrentHashMap<>();

    /**
     * 消费文档处理任务。
     *
     * @param taskMessage 任务消息体
     * @param channel     RabbitMQ Channel（用于手动 ACK/NACK）
     * @param message     Spring AMQP Message（可读取 redelivered 标志）
     * @param deliveryTag 投递标签
     */
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

            // 成功：ACK + 清理计数器
            channel.basicAck(deliveryTag, false);
            retryCounters.remove(taskId);
            log.info("Document task ACKed | taskId={}", taskId);

        } catch (Throwable t) {
            // Catch Throwable（不仅 Exception）—— 覆盖 OOM 等 Error 子类
            log.error("Document task failed | taskId={}, errorType={}, error={}",
                    taskId, t.getClass().getSimpleName(), t.getMessage(), t);
            handleFailure(taskMessage, channel, deliveryTag, t);
        }
    }

    // ---- 业务处理 ----

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

        updateDocumentStatus(documentId, "COMPLETED", chunks.size());
        progressManager.completeTask(taskId, chunks.size());
    }

    @Transactional
    public void updateDocumentStatus(Long documentId, String status, int chunkCount) {
        DocumentEntity doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("文档不存在: " + documentId));
        doc.setStatus(status);
        doc.setChunkCount(chunkCount);
        documentRepository.save(doc);
    }

    // ---- 失败处理与重试策略 ----

    /**
     * 处理失败的核心逻辑。
     *
     * <h3>判决策略</h3>
     * <table>
     *   <tr><th>错误类型</th><th>行为</th></tr>
     *   <tr><td>非瞬态（文件不存在、内容过大）</td><td>直接 NACK → DLX</td></tr>
     *   <tr><td>瞬态（OOM、超时、网络异常）</td><td>重试（最大 3 次）</td></tr>
     *   <tr><td>瞬态 + 重试耗尽</td><td>NACK → DLX</td></tr>
     * </table>
     */
    private void handleFailure(DocumentTaskMessage taskMessage, Channel channel,
                               long deliveryTag, Throwable t) {
        String taskId = taskMessage.getTaskId();

        try {
            // 非瞬态错误 → 直接 DLX，不重试
            if (isNonRetryableError(t)) {
                log.warn("Non-retryable error, NACK → DLX | taskId={}, error={}",
                        taskId, t.getMessage());
                failAndGoToDlx(taskMessage, channel, deliveryTag, t);
                return;
            }

            // 瞬态错误 → 检查重试次数
            int currentRetries = retryCounters.merge(taskId, 1, Integer::sum);

            if (currentRetries <= RabbitMQConfig.MAX_RETRY_COUNT) {
                // 重试：requeue=true，消息回到队列头部等待重新投递
                log.info("Retrying | taskId={}, attempt={}/{}, error={}",
                        taskId, currentRetries, RabbitMQConfig.MAX_RETRY_COUNT, t.getMessage());
                channel.basicNack(deliveryTag, false, true); // requeue

            } else {
                // 重试耗尽 → DLX
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

    /**
     * 标记任务失败并送入 DLX。
     * 更新 Redis progress + DB status → NACK(requeue=false)。
     */
    private void failAndGoToDlx(DocumentTaskMessage taskMessage, Channel channel,
                                long deliveryTag, Throwable t) {
        String taskId = taskMessage.getTaskId();

        // 1. 更新 Redis 进度（前端可见）
        progressManager.failTask(taskId, t.getMessage());

        // 2. 更新 DB 状态
        try {
            updateDocumentStatusOnError(taskMessage.getDocumentId());
        } catch (Exception dbEx) {
            log.error("Failed to update DB on failure | documentId={}",
                    taskMessage.getDocumentId(), dbEx);
        }

        // 3. 清理计数器
        retryCounters.remove(taskId);

        // 4. NACK → DLX
        try {
            channel.basicNack(deliveryTag, false, false); // requeue=false → DLX
        } catch (Exception nackEx) {
            log.error("NACK failed | taskId={}", taskId, nackEx);
        }
    }

    /**
     * 判断是否为非瞬态（不可重试）错误。
     */
    private boolean isNonRetryableError(Throwable t) {
        String message = (t.getMessage() != null ? t.getMessage() : "")
                + " " + getRootCauseMessage(t);
        return message.contains("文件不存在")
                || message.contains("文档内容过大");
    }

    private String getRootCauseMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : "";
    }

    @Transactional
    public void updateDocumentStatusOnError(Long documentId) {
        try {
            DocumentEntity doc = documentRepository.findById(documentId).orElse(null);
            if (doc != null) {
                doc.setStatus("FAILED");
                documentRepository.save(doc);
            }
        } catch (Exception e) {
            log.error("Failed to update document error status | documentId={}", documentId, e);
        }
    }
}
