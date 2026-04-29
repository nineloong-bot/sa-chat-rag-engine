package com.sa.assistant.common.exception;

/**
 * 可重试的文档处理异常。
 * 网络超时、OOM 等瞬态错误可以重试。
 */
public class RetryableDocumentException extends BusinessException {

    public RetryableDocumentException(String message, Throwable cause) {
        super(500, message);
        initCause(cause);
    }
}
