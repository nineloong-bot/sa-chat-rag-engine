package com.sa.assistant.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RagQueryRequest {

    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题长度不能超过2000字")
    private String question;

    private Long documentId;

    private Integer topK = 5;
}
