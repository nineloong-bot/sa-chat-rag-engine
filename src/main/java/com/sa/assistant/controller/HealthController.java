package com.sa.assistant.controller;

import com.sa.assistant.common.result.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/health")
    public R<Map<String, Object>> health() {
        return R.ok(Map.of(
                "status", "UP",
                "timestamp", LocalDateTime.now().toString(),
                "application", "SA Intelligent Assistant"
        ));
    }
}
