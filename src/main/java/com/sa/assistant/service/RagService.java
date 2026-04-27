package com.sa.assistant.service;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RagService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    private static final double SIMILARITY_THRESHOLD = 0.75;
    private static final int DEFAULT_TOP_K = 5;

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            你是一个专业的智能助手。请根据以下参考资料回答用户的问题。

            要求：
            1. 仅基于提供的参考资料进行回答，不要编造信息
            2. 如果参考资料中没有相关信息，请明确告知用户
            3. 回答要准确、完整、有条理
            4. 如果参考资料中有多个相关信息，请综合整理后给出回答

            参考资料：
            {context}
            """;

    private static final String FALLBACK_SYSTEM_PROMPT = """
            你是一个专业的智能助手。请根据你的知识回答用户的问题。
            注意：当前文档检索服务不可用，以下回答基于模型自身知识，可能不完全准确。
            """;

    public RagService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
    }

    public RagResponse ask(String question) {
        return ask(question, null, DEFAULT_TOP_K);
    }

    public RagResponse ask(String question, Long documentId, int topK) {
        log.info("RAG query received | question={}, documentId={}, topK={}", question, documentId, topK);

        List<Document> relevantDocs = retrieveFromVectorStore(question, documentId, topK);

        if (relevantDocs.isEmpty()) {
            log.warn("No relevant documents found above threshold | threshold={}", SIMILARITY_THRESHOLD);
            return buildFallbackResponse(question);
        }

        String context = relevantDocs.stream()
                .map(doc -> {
                    String content = doc.getText();
                    String docId = String.valueOf(doc.getMetadata().get("documentId"));
                    String chunkIdx = String.valueOf(doc.getMetadata().get("chunkIndex"));
                    return "[文档ID:" + docId + " | 分块:" + chunkIdx + "]\n" + content;
                })
                .collect(Collectors.joining("\n\n---\n\n"));

        log.info("Context assembled | relevantDocs={}, contextLength={}", relevantDocs.size(), context.length());

        String systemPrompt = SYSTEM_PROMPT_TEMPLATE.replace("{context}", context);

        try {
            String answer = chatClient.prompt()
                    .system(systemPrompt)
                    .user(question)
                    .call()
                    .content();

            log.info("RAG response generated | answerLength={}", answer != null ? answer.length() : 0);

            return RagResponse.builder()
                    .answer(answer)
                    .source("RAG")
                    .relevantChunkCount(relevantDocs.size())
                    .contextPreview(context.length() > 200 ? context.substring(0, 200) + "..." : context)
                    .build();
        } catch (Exception e) {
            log.error("LLM call failed in RAG mode | error={}", e.getMessage(), e);
            return RagResponse.builder()
                    .answer("抱歉，大模型服务暂时不可用，请稍后重试。错误信息：" + e.getMessage())
                    .source("ERROR")
                    .relevantChunkCount(relevantDocs.size())
                    .contextPreview(context.length() > 200 ? context.substring(0, 200) + "..." : context)
                    .build();
        }
    }

    private List<Document> retrieveFromVectorStore(String question, Long documentId, int topK) {
        try {
            SearchRequest.Builder requestBuilder = SearchRequest.builder()
                    .query(question)
                    .topK(topK)
                    .similarityThreshold(SIMILARITY_THRESHOLD);

            if (documentId != null) {
                FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();
                requestBuilder.filterExpression(
                        filterBuilder.eq("documentId", String.valueOf(documentId)).build()
                );
            }

            List<Document> results = vectorStore.similaritySearch(requestBuilder.build());
            log.info("Vector search completed | query={}, results={}", question, results.size());
            return results;

        } catch (Exception e) {
            log.error("ChromaDB retrieval failed, falling back to direct LLM | error={}", e.getMessage(), e);
            return List.of();
        }
    }

    private RagResponse buildFallbackResponse(String question) {
        log.warn("Falling back to direct LLM mode (no RAG context)");

        try {
            String answer = chatClient.prompt()
                    .system(FALLBACK_SYSTEM_PROMPT)
                    .user(question)
                    .call()
                    .content();

            return RagResponse.builder()
                    .answer(answer)
                    .source("FALLBACK")
                    .relevantChunkCount(0)
                    .contextPreview("文档检索不可用，已切换为通用大模型直连模式")
                    .build();
        } catch (Exception e) {
            log.error("LLM call failed in fallback mode | error={}", e.getMessage(), e);
            return RagResponse.builder()
                    .answer("抱歉，文档检索服务和大模型服务均不可用，请稍后重试。错误信息：" + e.getMessage())
                    .source("ERROR")
                    .relevantChunkCount(0)
                    .contextPreview("所有AI服务不可用")
                    .build();
        }
    }

    @Data
    @Builder
    public static class RagResponse {
        private String answer;
        private String source;
        private int relevantChunkCount;
        private String contextPreview;
    }
}
