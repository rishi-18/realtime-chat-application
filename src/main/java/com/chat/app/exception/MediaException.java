package com.chat.app.exception;

import lombok.Getter;

@Getter
public class MediaException extends RuntimeException {

    private final String errorCode;

    public MediaException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public MediaException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
