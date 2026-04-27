package com.sa.assistant.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatHistoryCreateDTO {

    @NotBlank(message = "sessionId 不能为空")
    @Size(max = 64)
    private String sessionId;

    @NotBlank(message = "role 不能为空")
    @Size(max = 16)
    private String role;

    @NotBlank(message = "content 不能为空")
    private String content;

    @Size(max = 32)
    private String model;

    private Integer tokenUsage;

    private java.util.Map<String, Object> metadata;
}
