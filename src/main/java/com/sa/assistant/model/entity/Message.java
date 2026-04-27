package com.sa.assistant.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * TODO: 会话消息实体 —— 预留功能，暂无对应的 Service/Controller。
 * 计划用于替代 ChatHistory 的精细化消息存储（按 conversation 聚合），当前仅定义数据模型。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "messages")
public class Message extends BaseEntity {

    @Column(nullable = false)
    private Long conversationId;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(length = 32)
    private String tokenCount;
}
