package com.sa.assistant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sa.assistant.common.ratelimit.RateLimit;
import com.sa.assistant.common.ratelimit.RateLimitKeyStrategy;
import com.sa.assistant.common.result.R;
import com.sa.assistant.model.dto.RagQueryRequest;
import com.sa.assistant.service.RagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;

@Slf4j
@RestController
@RequestMapping("/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;
    private final ObjectMapper objectMapper;

    @PostMapping("/ask")
    @RateLimit(maxRequests = 20, windowSeconds = 60, keyPrefix = "rag", keyStrategy = RateLimitKeyStrategy.IP)
    public R<RagService.RagResponse> ask(@Valid @RequestBody RagQueryRequest request) {
        RagService.RagResponse response = ragService.ask(
                request.getQuestion(),
                request.getDocumentId(),
                request.getTopK() != null ? request.getTopK() : 5
        );
        return R.ok(response);
    }

    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(maxRequests = 20, windowSeconds = 60, keyPrefix = "rag", keyStrategy = RateLimitKeyStrategy.IP)
    public Flux<ServerSentEvent<String>> askStream(@Valid @RequestBody RagQueryRequest request) {
        return ragService.askStream(
                request.getQuestion(),
                request.getDocumentId(),
                request.getTopK() != null ? request.getTopK() : 5
        ).map(event -> {
            try {
                return ServerSentEvent.<String>builder()
                        .data(objectMapper.writeValueAsString(event))
                        .build();
            } catch (Exception e) {
                log.error("Failed to serialize stream event", e);
                return ServerSentEvent.<String>builder()
                        .data("{\"type\":\"error\",\"message\":\"序列化失败\"}")
                        .build();
            }
        }).timeout(Duration.ofMinutes(5));
    }
}
