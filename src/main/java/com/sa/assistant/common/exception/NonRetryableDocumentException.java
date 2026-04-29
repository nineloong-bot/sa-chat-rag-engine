package com.sa.assistant.common.exception;

/**
 * 不可重试的文档处理异常。
 * 文件不存在、内容过大等结构性错误不应重试。
 */
public class NonRetryableDocumentException extends BusinessException {

    public NonRetryableDocumentException(String message) {
        super(400, message);
    }
}
