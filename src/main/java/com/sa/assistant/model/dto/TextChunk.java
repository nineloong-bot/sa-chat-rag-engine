package com.sa.assistant.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextChunk {

    private Long documentId;

    private int chunkIndex;

    private String content;

    private int startOffset;

    private int endOffset;
}
