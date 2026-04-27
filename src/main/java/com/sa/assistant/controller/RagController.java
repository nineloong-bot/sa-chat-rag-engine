package com.sa.assistant.controller;

import com.sa.assistant.common.ratelimit.RateLimit;
import com.sa.assistant.common.ratelimit.RateLimitKeyStrategy;
import com.sa.assistant.common.result.R;
import com.sa.assistant.model.dto.RagQueryRequest;
import com.sa.assistant.service.RagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

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
}
