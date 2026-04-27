package com.sa.assistant.controller;

import com.sa.assistant.common.ratelimit.RateLimit;
import com.sa.assistant.common.ratelimit.RateLimitKeyStrategy;
import com.sa.assistant.common.result.R;
import com.sa.assistant.model.dto.ChatHistoryCreateDTO;
import com.sa.assistant.model.dto.ChatHistoryUpdateDTO;
import com.sa.assistant.model.entity.ChatHistory;
import com.sa.assistant.service.ChatHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chat-history")
@RequiredArgsConstructor
public class ChatHistoryController {

    private final ChatHistoryService chatHistoryService;

    @PostMapping
    @RateLimit(maxRequests = 30, windowSeconds = 60, keyPrefix = "chat", keyStrategy = RateLimitKeyStrategy.IP)
    public R<ChatHistory> create(@Valid @RequestBody ChatHistoryCreateDTO dto) {
        return R.ok(chatHistoryService.create(dto));
    }

    @GetMapping("/{id}")
    public R<ChatHistory> getById(@PathVariable Long id) {
        return R.ok(chatHistoryService.getById(id));
    }

    @GetMapping("/session/{sessionId}")
    public R<List<ChatHistory>> listBySessionId(@PathVariable String sessionId) {
        return R.ok(chatHistoryService.listBySessionId(sessionId));
    }

    @PutMapping("/{id}")
    public R<ChatHistory> update(@PathVariable Long id, @Valid @RequestBody ChatHistoryUpdateDTO dto) {
        return R.ok(chatHistoryService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        chatHistoryService.delete(id);
        return R.ok();
    }
}
