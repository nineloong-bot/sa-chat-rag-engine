-- ChatHistory: 对话记录表，存储每轮对话的完整信息
-- 设计要点：
--   1. session_id 关联一次会话（一个用户可能有多轮会话）
--   2. role 区分 USER / ASSISTANT / SYSTEM
--   3. 使用 JSON 存储 metadata（如 token 用量、模型参数等），避免频繁加列
--   4. 添加 is_deleted 逻辑删除字段，避免物理删除导致缓存不一致

USE `sa_assistant`;

CREATE TABLE IF NOT EXISTS `chat_history` (
    `id`            BIGINT NOT NULL AUTO_INCREMENT,
    `session_id`    VARCHAR(64) NOT NULL COMMENT '会话标识，一次完整对话的ID',
    `role`          VARCHAR(16) NOT NULL COMMENT '角色: USER / ASSISTANT / SYSTEM',
    `content`       TEXT NOT NULL COMMENT '对话内容',
    `model`         VARCHAR(32) COMMENT '使用的模型名称',
    `token_usage`   INT DEFAULT 0 COMMENT '本条消息消耗的 token 数',
    `metadata`      JSON COMMENT '扩展元数据(模型参数、耗时等)',
    `is_deleted`    TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-正常, 1-已删除',
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_session_role` (`session_id`, `role`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话记录表';
