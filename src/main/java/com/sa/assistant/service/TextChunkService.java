package com.sa.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sa.assistant.model.dto.TextChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class TextChunkService {

    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_OVERLAP_SIZE = 50;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private final String chromaBaseUrl;
    private final String collectionName;
    private final String ollamaBaseUrl;
    private final String embeddingModel;

    private volatile String collectionId;

    public TextChunkService(
            @Value("${spring.ai.vectorstore.chroma.client.host:http://localhost}") String chromaHost,
            @Value("${spring.ai.vectorstore.chroma.client.port:8000}") int chromaPort,
            @Value("${spring.ai.vectorstore.chroma.collection-name:sa_documents}") String collectionName,
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
            @Value("${spring.ai.ollama.embedding.options.model:nomic-embed-text}") String embeddingModel,
            ObjectMapper objectMapper) {
        this.chromaBaseUrl = chromaHost + ":" + chromaPort;
        this.collectionName = collectionName;
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.embeddingModel = embeddingModel;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(this.chromaBaseUrl).build();
    }

    public List<TextChunk> chunkText(String text, Long documentId) {
        return chunkText(text, documentId, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP_SIZE);
    }

    public List<TextChunk> chunkText(String text, Long documentId, int chunkSize, int overlapSize) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<TextChunk> chunks = new ArrayList<>();
        int textLength = text.length();
        int start = 0;
        int index = 0;

        while (start < textLength) {
            int end = Math.min(start + chunkSize, textLength);

            if (end < textLength) {
                int lastPeriod = text.lastIndexOf('。', end);
                int lastNewline = text.lastIndexOf('\n', end);
                int lastSpace = text.lastIndexOf(' ', end);
                int breakPoint = Math.max(Math.max(lastPeriod, lastNewline), lastSpace);

                if (breakPoint > start) {
                    end = breakPoint + 1;
                }
            }

            String chunkContent = text.substring(start, end).trim();
            if (!chunkContent.isEmpty()) {
                chunks.add(TextChunk.builder()
                        .documentId(documentId)
                        .chunkIndex(index)
                        .content(chunkContent)
                        .startOffset(start)
                        .endOffset(end)
                        .build());
                index++;
            }

            int nextStart = end - overlapSize;
            if (nextStart <= start) {
                nextStart = end;
            }
            start = nextStart;
        }

        log.info("Text chunked | documentId={}, totalChunks={}, textLength={}",
                documentId, chunks.size(), textLength);
        return chunks;
    }

    public void embedAndStore(List<TextChunk> chunks, Long documentId) {
        if (chunks == null || chunks.isEmpty()) {
            log.warn("No chunks to embed | documentId={}", documentId);
            return;
        }

        log.info("Embedding & storing chunks | documentId={}, chunkCount={}",
                documentId, chunks.size());

        try {
            ensureCollection();

            List<String> ids = new ArrayList<>();
            List<float[]> embeddings = new ArrayList<>();
            List<Map<String, String>> metadatas = new ArrayList<>();
            List<String> documents = new ArrayList<>();

            for (TextChunk chunk : chunks) {
                ids.add("doc" + documentId + "_" + chunk.getChunkIndex());
                embeddings.add(embedText(chunk.getContent()));
                Map<String, String> meta = new HashMap<>();
                meta.put("documentId", String.valueOf(documentId));
                meta.put("chunkIndex", String.valueOf(chunk.getChunkIndex()));
                meta.put("startOffset", String.valueOf(chunk.getStartOffset()));
                meta.put("endOffset", String.valueOf(chunk.getEndOffset()));
                metadatas.add(meta);
                documents.add(chunk.getContent());
            }

            upsertEmbeddings(ids, embeddings, metadatas, documents);

            log.info("Stored {} chunks to ChromaDB collection={}", chunks.size(), collectionName);
        } catch (Exception e) {
            log.error("Failed to embed & store | documentId={}, error={}",
                    documentId, e.getMessage(), e);
            throw new RuntimeException("向量化存储失败: " + e.getMessage(), e);
        }
    }

    private float[] embedText(String text) {
        try {
            Map<String, Object> request = Map.of(
                    "model", embeddingModel,
                    "prompt", text
            );
            JsonNode response = restClient.post()
                    .uri(ollamaBaseUrl + "/api/embeddings")
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
        } catch (Exception e) {
            throw new RuntimeException("Embedding failed: " + e.getMessage(), e);
        }
    }

    private synchronized void ensureCollection() {
        if (collectionId != null) return;

        // Try to get existing collection
        String getUrl = "/api/v2/tenants/default_tenant/databases/default_database/collections/" + collectionName;
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

        // Create collection
        String createUrl = "/api/v2/tenants/default_tenant/databases/default_database/collections";
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
            throw new RuntimeException("Failed to create collection: " + e.getMessage(), e);
        }
    }

    private void upsertEmbeddings(List<String> ids, List<float[]> embeddings,
                                   List<Map<String, String>> metadatas, List<String> documents) {
        String url = "/api/v2/tenants/default_tenant/databases/default_database/collections/"
                + collectionId + "/upsert";

        Map<String, Object> body = Map.of(
                "ids", ids,
                "embeddings", embeddings,
                "metadatas", metadatas,
                "documents", documents
        );

        restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        log.debug("Upserted {} embeddings to collection {}", ids.size(), collectionId);
    }
}
