package com.sa.assistant.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "chat_history")
public class ChatHistory extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String sessionId;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(length = 32)
    private String model;

    @Column
    private Integer tokenUsage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    private Map<String, Object> metadata;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;
}
