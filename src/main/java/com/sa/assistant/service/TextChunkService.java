package com.sa.assistant.service;

import com.sa.assistant.model.dto.TextChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TextChunkService {

    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_OVERLAP_SIZE = 50;

    private final VectorStore vectorStore;

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

        log.info("Embedding & storing chunks to ChromaDB | documentId={}, chunkCount={}",
                documentId, chunks.size());

        List<Document> documents = chunks.stream()
                .map(chunk -> new Document(
                        chunk.getContent(),
                        Map.of(
                                "documentId", String.valueOf(documentId),
                                "chunkIndex", String.valueOf(chunk.getChunkIndex()),
                                "startOffset", String.valueOf(chunk.getStartOffset()),
                                "endOffset", String.valueOf(chunk.getEndOffset())
                        )
                ))
                .toList();

        vectorStore.add(documents);

        log.info("Chunks embedded & stored to ChromaDB | documentId={}, chunkCount={}",
                documentId, chunks.size());
    }
}
