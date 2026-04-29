package com.sa.assistant.service;

import com.sa.assistant.infra.ChromaVectorStore;
import com.sa.assistant.infra.OllamaEmbeddingClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 封装向量检索的业务语义：先将问题向量化，再在 ChromaDB 中查询相似文档。
 *
 * <p>底层 HTTP 交互委托给 {@link ChromaVectorStore} 和 {@link OllamaEmbeddingClient}。
 */
@Slf4j
@Component
public class EmbeddingClient {

    private final ChromaVectorStore vectorStore;
    private final OllamaEmbeddingClient embeddingClient;

    public EmbeddingClient(ChromaVectorStore vectorStore, OllamaEmbeddingClient embeddingClient) {
        this.vectorStore = vectorStore;
        this.embeddingClient = embeddingClient;
    }

    /**
     * 查询与 question 最相似的 topK 个文档。
     * 发生异常时返回空列表，由调用方决定降级策略。
     */
    public List<Document> search(String question, Long documentId, int topK) {
        try {
            float[] queryEmbedding = embeddingClient.embed(question);
            return vectorStore.query(queryEmbedding, topK, documentId);
        } catch (Exception e) {
            log.error("Embedding search failed | question={}, error={}", question, e.getMessage(), e);
            return List.of();
        }
    }
}
