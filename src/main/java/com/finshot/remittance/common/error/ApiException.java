package com.finshot.remittance.common.error;

import org.springframework.http.HttpStatus;

/** Base class for errors we throw on purpose. Each subclass fixes the HTTP status and error code. */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
