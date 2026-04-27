package com.sa.assistant.common.lock;

import com.sa.assistant.common.exception.BusinessException;

public class LockAcquisitionException extends BusinessException {

    public LockAcquisitionException(String message) {
        super(429, message);
    }
}
