package com.sa.assistant.model.dto;

/**
 * 文档处理状态枚举。
 *
 * <pre>
 * 状态流转：
 *   PENDING → QUEUED → PROCESSING → COMPLETED
 *                                  → FAILED → (重试) → PROCESSING
 *                                  → FAILED → (耗尽) → DEAD
 * </pre>
 */
public enum DocumentStatus {

    PENDING,
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED,
    DEAD;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == DEAD;
    }

    public boolean isProcessing() {
        return this == PENDING || this == QUEUED || this == PROCESSING;
    }
}
