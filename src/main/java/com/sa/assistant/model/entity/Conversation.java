package com.sa.assistant.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * TODO: 会话管理实体 —— 预留功能，暂无对应的 Service/Controller。
 * 计划用于管理用户会话生命周期（创建/归档/统计），当前仅定义数据模型。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "conversations")
public class Conversation extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String sessionId;

    @Column(length = 128)
    private String title;

    @Column(nullable = false, length = 32)
    private String model;

    @Column(nullable = false, length = 16)
    private String status;
}
