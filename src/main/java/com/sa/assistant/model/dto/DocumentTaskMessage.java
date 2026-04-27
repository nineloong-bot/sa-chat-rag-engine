package com.sa.assistant.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentTaskMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long documentId;

    private String taskId;

    private String fileName;

    private String filePath;

    private String fileType;
}
