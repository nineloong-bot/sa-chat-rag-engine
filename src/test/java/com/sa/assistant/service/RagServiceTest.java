package com.sa.assistant.service;

import com.sa.assistant.service.RagService.RagResponse;
import com.sa.assistant.service.RagService.StreamEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RagService 单元测试")
class RagServiceTest {

    @Mock private EmbeddingClient embeddingClient;
    @Mock private ChatClient chatClient;

    private RagService ragService;

    private void stubChatResponse(String content) {
        ChatClient.ChatClientRequestSpec reqSpec = mock(ChatClient.ChatClientRequestSpec.class);
        when(chatClient.prompt()).thenReturn(reqSpec);
        when(reqSpec.system(anyString())).thenReturn(reqSpec);
        when(reqSpec.user(anyString())).thenReturn(reqSpec);
        when(reqSpec.call()).thenReturn(mock(ChatClient.CallResponseSpec.class));
        when(reqSpec.call().content()).thenReturn(content);
        ragService = new RagService(embeddingClient, chatClient);
    }

    private void stubChatResponseThrows(RuntimeException ex) {
        ChatClient.ChatClientRequestSpec reqSpec = mock(ChatClient.ChatClientRequestSpec.class);
        when(chatClient.prompt()).thenReturn(reqSpec);
        when(reqSpec.system(anyString())).thenReturn(reqSpec);
        when(reqSpec.user(anyString())).thenReturn(reqSpec);
        when(reqSpec.call()).thenThrow(ex);
        ragService = new RagService(embeddingClient, chatClient);
    }

    private void stubStreamResponse(Flux<String> chunks) {
        ChatClient.ChatClientRequestSpec reqSpec = mock(ChatClient.ChatClientRequestSpec.class);
        when(chatClient.prompt()).thenReturn(reqSpec);
        when(reqSpec.system(anyString())).thenReturn(reqSpec);
        when(reqSpec.user(anyString())).thenReturn(reqSpec);
        when(reqSpec.stream()).thenReturn(mock(ChatClient.StreamResponseSpec.class));
        when(reqSpec.stream().content()).thenReturn(chunks);
        ragService = new RagService(embeddingClient, chatClient);
    }

    private static Document makeDoc(String content, String docId, String chunkIdx) {
        return new Document(content, Map.of("documentId", docId, "chunkIndex", chunkIdx));
    }

    // ========================================
    // ask()
    // ========================================

    @Nested
    @DisplayName("检索到文档时 (RAG 模式)")
    class AskWithResults {

        @Test
        @DisplayName("应返回 source=RAG 并使用检索到的上下文生成回答")
        void shouldReturnRagAnswerWhenDocsFound() {
            when(embeddingClient.search(eq("范仲淹的边塞诗"), isNull(), eq(5)))
                    .thenReturn(List.of(makeDoc("范仲淹作品分析", "1", "0")));
            stubChatResponse("根据参考资料，范仲淹的边塞诗表达了思乡之情。");

            RagResponse response = ragService.ask("范仲淹的边塞诗");

            assertThat(response.getSource()).isEqualTo("RAG");
            assertThat(response.getRelevantChunkCount()).isEqualTo(1);
            assertThat(response.getAnswer()).contains("范仲淹");
            assertThat(response.getContextPreview()).isNotEmpty();
        }

        @Test
        @DisplayName("指定 documentId 时应传参到 EmbeddingClient")
        void shouldPassDocumentIdToSearch() {
            when(embeddingClient.search(eq("问题"), eq(1L), eq(3)))
                    .thenReturn(List.of(makeDoc("内容", "1", "0")));
            stubChatResponse("filtered");

            RagResponse response = ragService.ask("问题", 1L, 3);

            assertThat(response.getSource()).isEqualTo("RAG");
            verify(embeddingClient).search("问题", 1L, 3);
        }
    }

    @Nested
    @DisplayName("未检索到文档时 (Fallback 模式)")
    class AskFallback {

        @Test
        @DisplayName("EmbeddingClient 返回空列表时进入 Fallback")
        void shouldFallbackWhenNoDocs() {
            when(embeddingClient.search(anyString(), any(), anyInt()))
                    .thenReturn(List.of());
            stubChatResponse("泛化回答...");

            RagResponse response = ragService.ask("冷门问题");

            assertThat(response.getSource()).isEqualTo("FALLBACK");
            assertThat(response.getRelevantChunkCount()).isZero();
            assertThat(response.getContextPreview()).contains("文档检索不可用");
        }
    }

    @Nested
    @DisplayName("LLM 调用异常处理")
    class AskErrorHandling {

        @Test
        @DisplayName("LLM 失败时返回 source=ERROR 并包含错误信息")
        void shouldReturnErrorWhenLlmFails() {
            when(embeddingClient.search(anyString(), any(), anyInt()))
                    .thenReturn(List.of(makeDoc("内容", "1", "0")));
            stubChatResponseThrows(new RuntimeException("LLM timeout"));

            RagResponse response = ragService.ask("测试问题");

            assertThat(response.getSource()).isEqualTo("ERROR");
            assertThat(response.getAnswer()).contains("LLM timeout");
        }
    }

    // ========================================
    // askStream()
    // ========================================

    @Nested
    @DisplayName("流式问答")
    class AskStream {

        @Test
        @DisplayName("检索到文档时应流式返回 chunk 事件")
        void shouldStreamChunks() {
            when(embeddingClient.search(anyString(), any(), anyInt()))
                    .thenReturn(List.of(makeDoc("内容", "1", "0")));
            stubStreamResponse(Flux.just("你好", "世界"));

            Flux<StreamEvent> stream = ragService.askStream("你好吗", null, 3);
            StreamEvent first = stream.blockFirst(Duration.ofSeconds(5));

            assertThat(first).isNotNull();
            assertThat(first.type()).isEqualTo("chunk");
            assertThat(first.content()).isEqualTo("你好");
        }

        @Test
        @DisplayName("无检索结果时应走 Fallback 流式")
        void shouldStreamFallback() {
            when(embeddingClient.search(anyString(), any(), anyInt()))
                    .thenReturn(List.of());
            stubStreamResponse(Flux.just("fallback content"));

            Flux<StreamEvent> stream = ragService.askStream("问题", null, 3);
            StreamEvent first = stream.blockFirst(Duration.ofSeconds(5));

            assertThat(first).isNotNull();
            assertThat(first.content()).isEqualTo("fallback content");
        }

        @Test
        @DisplayName("流式出错时应返回 error 事件")
        void shouldReturnErrorEventOnStreamFailure() {
            when(embeddingClient.search(anyString(), any(), anyInt()))
                    .thenReturn(List.of(makeDoc("内容", "1", "0")));
            stubStreamResponse(Flux.error(new RuntimeException("Stream broken")));

            Flux<StreamEvent> stream = ragService.askStream("问题", null, 3);
            StreamEvent event = stream.blockFirst(Duration.ofSeconds(5));

            assertThat(event).isNotNull();
            assertThat(event.type()).isEqualTo("error");
            assertThat(event.message()).contains("Stream broken");
        }
    }
}
