package com.sa.assistant.common.ratelimit;

import com.sa.assistant.common.exception.BusinessException;

public class RateLimitExceededException extends BusinessException {

    public RateLimitExceededException(String message) {
        super(429, message);
    }
}
