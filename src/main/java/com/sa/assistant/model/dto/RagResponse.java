package com.sa.assistant.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RagResponse {
    private String answer;
    private String source;
    private int relevantChunkCount;
    private String contextPreview;
}
