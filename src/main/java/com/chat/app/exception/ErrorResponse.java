package com.chat.app.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ErrorResponse {
    @Builder.Default
    private final String timestamp = Instant.now().toString();
    private final int status;
    private final String error;
    private final String message;
    private final String code;
    private final List<?> details;
}
