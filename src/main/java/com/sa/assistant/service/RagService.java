package com.sa.assistant.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RagService {

    private final EmbeddingClient embeddingClient;
    private final ChatClient chatClient;

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

    @Autowired
    public RagService(EmbeddingClient embeddingClient, ChatClient.Builder chatClientBuilder) {
        this.embeddingClient = embeddingClient;
        this.chatClient = chatClientBuilder.build();
    }

    /** Package-private constructor for testing. */
    RagService(EmbeddingClient embeddingClient, ChatClient chatClient) {
        this.embeddingClient = embeddingClient;
        this.chatClient = chatClient;
    }

    public RagResponse ask(String question) {
        return ask(question, null, DEFAULT_TOP_K);
    }

    public RagResponse ask(String question, Long documentId, int topK) {
        log.info("RAG query received | question={}, documentId={}, topK={}", question, documentId, topK);

        List<Document> relevantDocs = embeddingClient.search(question, documentId, topK);

        if (relevantDocs.isEmpty()) {
            log.warn("No relevant documents found");
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

    public Flux<StreamEvent> askStream(String question, Long documentId, int topK) {
        log.info("RAG stream query received | question={}, documentId={}, topK={}", question, documentId, topK);

        List<Document> relevantDocs = embeddingClient.search(question, documentId, topK);

        if (relevantDocs.isEmpty()) {
            log.warn("No relevant documents found, streaming in fallback mode");
            return streamFallback(question);
        }

        String context = relevantDocs.stream()
                .map(doc -> {
                    String content = doc.getText();
                    String docId = String.valueOf(doc.getMetadata().get("documentId"));
                    String chunkIdx = String.valueOf(doc.getMetadata().get("chunkIndex"));
                    return "[文档ID:" + docId + " | 分块:" + chunkIdx + "]\n" + content;
                })
                .collect(Collectors.joining("\n\n---\n\n"));

        log.info("Stream context assembled | relevantDocs={}, contextLength={}", relevantDocs.size(), context.length());

        String systemPrompt = SYSTEM_PROMPT_TEMPLATE.replace("{context}", context);
        String contextPreview = context.length() > 200 ? context.substring(0, 200) + "..." : context;

        return chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .stream()
                .content()
                .map(StreamEvent::chunk)
                .concatWith(Flux.just(
                        StreamEvent.source("RAG", relevantDocs.size(), contextPreview),
                        StreamEvent.done()
                ))
                .onErrorResume(e -> {
                    log.error("Stream error in RAG mode | error={}", e.getMessage(), e);
                    return Flux.just(StreamEvent.error("流式输出出错：" + e.getMessage()));
                });
    }

    private Flux<StreamEvent> streamFallback(String question) {
        return chatClient.prompt()
                .system(FALLBACK_SYSTEM_PROMPT)
                .user(question)
                .stream()
                .content()
                .map(StreamEvent::chunk)
                .concatWith(Flux.just(
                        StreamEvent.source("FALLBACK", 0, "文档检索不可用，已切换为通用大模型直连模式"),
                        StreamEvent.done()
                ))
                .onErrorResume(e -> {
                    log.error("Stream error in fallback mode | error={}", e.getMessage(), e);
                    return Flux.just(StreamEvent.error("大模型服务不可用：" + e.getMessage()));
                });
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StreamEvent(
            String type,
            String content,
            String source,
            Integer relevantChunkCount,
            String contextPreview,
            String message
    ) {
        public static StreamEvent chunk(String content) {
            return new StreamEvent("chunk", content, null, null, null, null);
        }

        public static StreamEvent source(String source, int relevantChunkCount, String contextPreview) {
            return new StreamEvent("source", null, source, relevantChunkCount, contextPreview, null);
        }

        public static StreamEvent done() {
            return new StreamEvent("done", null, null, null, null, null);
        }

        public static StreamEvent error(String message) {
            return new StreamEvent("error", null, null, null, null, message);
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
