package com.chat.app.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypingRequest {
    @NotNull(message = "Room ID is required")
    private UUID roomId;

    @NotNull(message = "Typing status is required")
    private Boolean typing;
}
