package com.sa.assistant.service;

import com.sa.assistant.infra.ChromaVectorStore;
import com.sa.assistant.infra.OllamaEmbeddingClient;
import com.sa.assistant.model.dto.TextChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文本切块与向量化存储服务。
 *
 * <p>职责单一化：
 * <ul>
 *   <li>文本切块算法（本类核心逻辑）</li>
 *   <li>向量化存储编排（委托给基础设施层）</li>
 * </ul>
 *
 * <p>ChromaDB 和 Ollama 的 HTTP 交互由 {@link ChromaVectorStore} 和
 * {@link OllamaEmbeddingClient} 统一封装。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TextChunkService {

    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_OVERLAP_SIZE = 50;

    private final ChromaVectorStore vectorStore;
    private final OllamaEmbeddingClient embeddingClient;

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
                int breakPoint = findBreakPoint(text, start, end);
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

    /**
     * 将切块向量化并写入 ChromaDB。
     */
    public void embedAndStore(List<TextChunk> chunks, Long documentId) {
        if (chunks == null || chunks.isEmpty()) {
            log.warn("No chunks to embed | documentId={}", documentId);
            return;
        }

        log.info("Embedding & storing chunks | documentId={}, chunkCount={}",
                documentId, chunks.size());

        try {
            List<String> ids = new ArrayList<>();
            List<float[]> embeddings = new ArrayList<>();
            List<Map<String, String>> metadatas = new ArrayList<>();
            List<String> documents = new ArrayList<>();

            for (TextChunk chunk : chunks) {
                ids.add("doc" + documentId + "_" + chunk.getChunkIndex());
                embeddings.add(embeddingClient.embed(chunk.getContent()));
                metadatas.add(buildMetadata(documentId, chunk));
                documents.add(chunk.getContent());
            }

            vectorStore.upsert(ids, embeddings, metadatas, documents);

            log.info("Stored {} chunks to ChromaDB", chunks.size());
        } catch (Exception e) {
            log.error("Failed to embed & store | documentId={}, error={}",
                    documentId, e.getMessage(), e);
            throw new RuntimeException("向量化存储失败: " + e.getMessage(), e);
        }
    }

    private int findBreakPoint(String text, int start, int end) {
        int lastPeriod = text.lastIndexOf('。', end);
        int lastNewline = text.lastIndexOf('\n', end);
        int lastSpace = text.lastIndexOf(' ', end);
        return Math.max(Math.max(lastPeriod, lastNewline), lastSpace);
    }

    private Map<String, String> buildMetadata(Long documentId, TextChunk chunk) {
        Map<String, String> meta = new HashMap<>();
        meta.put("documentId", String.valueOf(documentId));
        meta.put("chunkIndex", String.valueOf(chunk.getChunkIndex()));
        meta.put("startOffset", String.valueOf(chunk.getStartOffset()));
        meta.put("endOffset", String.valueOf(chunk.getEndOffset()));
        return meta;
    }
}
