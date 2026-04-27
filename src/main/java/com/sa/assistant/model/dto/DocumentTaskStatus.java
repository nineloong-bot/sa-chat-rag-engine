package com.sa.assistant.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentTaskStatus {

    private String taskId;

    private Long documentId;

    private String status;

    private Integer progress;

    private String message;

    private Integer chunkCount;

    private String errorMessage;
}
