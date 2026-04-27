package com.sa.assistant.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatHistoryUpdateDTO {

    @NotBlank(message = "content 不能为空")
    private String content;

    @Size(max = 32)
    private String model;

    private Integer tokenUsage;

    private java.util.Map<String, Object> metadata;
}
