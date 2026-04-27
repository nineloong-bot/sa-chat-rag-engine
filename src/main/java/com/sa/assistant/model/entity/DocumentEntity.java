package com.sa.assistant.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "documents")
public class DocumentEntity extends BaseEntity {

    @Column(nullable = false, length = 256)
    private String fileName;

    @Column(nullable = false, length = 64)
    private String fileType;

    @Column(nullable = false)
    private Long fileSize;

    @Column(length = 512)
    private String filePath;

    @Column(nullable = false, length = 32)
    private String status;

    @Column
    private Integer chunkCount;
}
