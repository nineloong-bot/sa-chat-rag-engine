package com.sa.assistant.infra;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Ollama Embedding 统一客户端。
 *
 * <p>封装文本向量化调用，其他服务通过此客户端获取 embedding，
 * 不再各自构建 RestClient 和解析响应。
 */
@Slf4j
@Component
public class OllamaEmbeddingClient {

    private final RestClient restClient;
    private final String model;

    public OllamaEmbeddingClient(
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${spring.ai.ollama.embedding.options.model:nomic-embed-text}") String model) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.model = model;
    }

    /**
     * 将文本转换为向量。
     */
    public float[] embed(String text) {
        JsonNode response = restClient.post()
                .uri("/api/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", model, "prompt", text))
                .retrieve()
                .body(JsonNode.class);

        JsonNode embedding = response.get("embedding");
        float[] result = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            result[i] = (float) embedding.get(i).asDouble();
        }
        return result;
    }
}
