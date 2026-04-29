package com.sa.assistant.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ChromaDB 向量存储统一客户端。
 *
 * <p>封装所有 ChromaDB HTTP 交互（collection 管理、upsert、query），
 * 其他服务通过此客户端操作向量库，不再各自构建 RestClient。
 */
@Slf4j
@Component
public class ChromaVectorStore {

    private static final String API_PREFIX = "/api/v2/tenants/default_tenant/databases/default_database/collections";

    private final RestClient restClient;
    private final String collectionName;
    private final ObjectMapper objectMapper;
    private volatile String collectionId;

    public ChromaVectorStore(
            @Value("${spring.ai.vectorstore.chroma.client.host:http://localhost}") String host,
            @Value("${spring.ai.vectorstore.chroma.client.port:8000}") int port,
            @Value("${spring.ai.vectorstore.chroma.collection-name:sa_documents}") String collectionName,
            ObjectMapper objectMapper) {
        this.restClient = RestClient.builder().baseUrl(host + ":" + port).build();
        this.collectionName = collectionName;
        this.objectMapper = objectMapper;
    }

    /**
     * 向 ChromaDB 写入/更新向量数据。
     */
    public void upsert(List<String> ids, List<float[]> embeddings,
                       List<Map<String, String>> metadatas, List<String> documents) {
        ensureCollection();
        String url = API_PREFIX + "/" + collectionId + "/upsert";

        restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "ids", ids,
                        "embeddings", embeddings,
                        "metadatas", metadatas,
                        "documents", documents
                ))
                .retrieve()
                .toBodilessEntity();

        log.debug("Upserted {} embeddings to collection {}", ids.size(), collectionId);
    }

    /**
     * 查询与 queryEmbedding 最相似的 topK 个文档。
     *
     * @return 匹配的文档列表，异常时返回空列表
     */
    public List<Document> query(float[] queryEmbedding, int topK, Long documentId) {
        try {
            ensureCollection();

            Map<String, Object> body = new HashMap<>();
            body.put("query_embeddings", List.of(queryEmbedding));
            body.put("n_results", topK);
            if (documentId != null) {
                body.put("where", Map.of("documentId", String.valueOf(documentId)));
            }

            String url = API_PREFIX + "/" + collectionId + "/query";

            JsonNode response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
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
            log.error("ChromaDB query failed | error={}", e.getMessage(), e);
            return List.of();
        }
    }

    private synchronized void ensureCollection() {
        if (collectionId != null) return;

        String getUrl = API_PREFIX + "/" + collectionName;
        try {
            JsonNode col = restClient.get().uri(getUrl).retrieve().body(JsonNode.class);
            if (col != null && col.has("id")) {
                collectionId = col.get("id").asText();
                log.info("Found existing ChromaDB collection: {} (id={})", collectionName, collectionId);
                return;
            }
        } catch (Exception e) {
            log.info("Collection '{}' not found, creating...", collectionName);
        }

        String createUrl = API_PREFIX;
        Map<String, String> body = Map.of("name", collectionName);
        try {
            JsonNode created = restClient.post()
                    .uri(createUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            collectionId = created.get("id").asText();
            log.info("Created ChromaDB collection: {} (id={})", collectionName, collectionId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create ChromaDB collection: " + e.getMessage(), e);
        }
    }
}
