package com.finshot.remittance.common.error;

import org.springframework.http.HttpStatus;

public class LimitExceededException extends ApiException {

    public LimitExceededException(String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "LIMIT_EXCEEDED", message);
    }
}
