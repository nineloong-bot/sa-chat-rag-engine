package com.sa.assistant.service;

import com.sa.assistant.service.RagService.RagResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RagService 单元测试")
class RagServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ChatClient.Builder chatClientBuilder;

    /**
     * 构建并注入完全 Mock 的 RagService。
     * RagService 的构造函数会立即调用 chatClientBuilder.build()，
     * 因此必须在构造前完成 ChatClient 的 mock 链。
     */
    private RagService createRagService(String chatResponseContent) {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(chatResponseContent);

        return new RagService(vectorStore, chatClientBuilder);
    }

    private RagService createRagServiceWithError(RuntimeException exception) {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);

        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenThrow(exception);

        return new RagService(vectorStore, chatClientBuilder);
    }

    @Nested
    @DisplayName("Vector store 检索成功 + LLM 正常响应")
    class RetrieveAndAnswer {

        @Test
        @DisplayName("检索到相关文档时应返回 RAG 模式的回答")
        void shouldReturnRagAnswerWhenDocsFound() {
            Document doc = new Document("测试文档内容",
                    Map.of("documentId", "1", "chunkIndex", "0"));
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(List.of(doc));

            RagService ragService = createRagService("根据参考资料，答案是...");
            RagResponse response = ragService.ask("测试问题");

            assertThat(response.getSource()).isEqualTo("RAG");
            assertThat(response.getRelevantChunkCount()).isEqualTo(1);
            assertThat(response.getAnswer()).contains("根据参考资料");
        }
    }

    @Nested
    @DisplayName("Vector store 无结果时的 Fallback 处理")
    class FallbackWhenNoDocs {

        @Test
        @DisplayName("无相关文档时应通过 FALLBACK 模式直连 LLM")
        void shouldFallbackToDirectLlmWhenNoDocs() {
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(List.of());

            RagService ragService = createRagService("我根据已有知识回答...");
            RagResponse response = ragService.ask("冷门问题");

            assertThat(response.getSource()).isEqualTo("FALLBACK");
            assertThat(response.getRelevantChunkCount()).isZero();
            assertThat(response.getContextPreview()).contains("文档检索不可用");
        }
    }

    @Nested
    @DisplayName("LLM 调用异常处理")
    class ErrorHandling {

        @Test
        @DisplayName("LLM 调用失败时返回 ERROR 来源的错误信息")
        void shouldReturnErrorResponseWhenLlmFails() {
            Document doc = new Document("内容",
                    Map.of("documentId", "1", "chunkIndex", "0"));
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(List.of(doc));

            RagService ragService = createRagServiceWithError(new RuntimeException("API timeout"));
            RagResponse response = ragService.ask("测试问题");

            assertThat(response.getSource()).isEqualTo("ERROR");
            assertThat(response.getAnswer()).contains("API timeout");
        }
    }
}
