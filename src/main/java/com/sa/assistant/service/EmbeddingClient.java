package com.sa.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 封装对 ChromaDB（向量检索）和 Ollama（文本嵌入）的直接 HTTP 调用，
 * 便于单元测试时 mock。
 */
@Component
public class EmbeddingClient {

    private final RestClient chromaClient;
    private final RestClient ollamaClient;
    private final String collectionName;
    private final String embeddingModel;
    private final ObjectMapper objectMapper;

    private volatile String collectionId;

    public EmbeddingClient(
            @Value("${spring.ai.vectorstore.chroma.client.host:http://localhost}") String chromaHost,
            @Value("${spring.ai.vectorstore.chroma.client.port:8000}") int chromaPort,
            @Value("${spring.ai.vectorstore.chroma.collection-name:sa_documents}") String collectionName,
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
            @Value("${spring.ai.ollama.embedding.options.model:nomic-embed-text}") String embeddingModel,
            ObjectMapper objectMapper) {
        this.chromaClient = RestClient.builder().baseUrl(chromaHost + ":" + chromaPort).build();
        this.ollamaClient = RestClient.builder().baseUrl(ollamaBaseUrl).build();
        this.collectionName = collectionName;
        this.embeddingModel = embeddingModel;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询 ChromaDB 中与 question 最相似的 topK 个文档。
     * 发生任何异常时返回空列表。
     */
    public List<Document> search(String question, Long documentId, int topK) {
        try {
            ensureCollectionId();

            float[] queryEmbedding = embed(question);

            Map<String, Object> queryBody = new java.util.HashMap<>();
            queryBody.put("query_embeddings", List.of(queryEmbedding));
            queryBody.put("n_results", topK);
            if (documentId != null) {
                queryBody.put("where", Map.of("documentId", String.valueOf(documentId)));
            }

            String queryUrl = "/api/v2/tenants/default_tenant/databases/default_database/collections/"
                    + collectionId + "/query";

            JsonNode response = chromaClient.post()
                    .uri(queryUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(queryBody)
                    .retrieve()
                    .body(JsonNode.class);

            List<Document> results = new ArrayList<>();
            JsonNode idsArray = response.get("ids").get(0);
            JsonNode docsArray = response.get("documents").get(0);
            JsonNode metasArray = response.get("metadatas").get(0);

            if (idsArray != null && idsArray.size() > 0) {
                for (int i = 0; i < idsArray.size(); i++) {
                    var doc = new Document(
                            docsArray.get(i).asText(),
                            objectMapper.convertValue(metasArray.get(i), Map.class)
                    );
                    results.add(doc);
                }
            }

            return results;

        } catch (Exception e) {
            return List.of();
        }
    }

    private float[] embed(String text) {
        Map<String, Object> request = Map.of("model", embeddingModel, "prompt", text);
        JsonNode response = ollamaClient.post()
                .uri("/api/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        JsonNode embedding = response.get("embedding");
        float[] result = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            result[i] = (float) embedding.get(i).asDouble();
        }
        return result;
    }

    private synchronized void ensureCollectionId() {
        if (collectionId != null) return;
        String getUrl = "/api/v2/tenants/default_tenant/databases/default_database/collections/" + collectionName;
        JsonNode col = chromaClient.get().uri(getUrl).retrieve().body(JsonNode.class);
        if (col != null && col.has("id")) {
            collectionId = col.get("id").asText();
        }
    }
}
