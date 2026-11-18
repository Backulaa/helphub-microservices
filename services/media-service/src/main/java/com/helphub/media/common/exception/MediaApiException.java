package com.helphub.media.common.exception;

import org.springframework.http.HttpStatus;

public class MediaApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public MediaApiException(
            HttpStatus status,
            String code,
            String message) {

        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
