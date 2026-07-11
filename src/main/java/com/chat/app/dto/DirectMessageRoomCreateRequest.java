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
public class DirectMessageRoomCreateRequest {

    @NotNull(message = "Recipient ID is required")
    private UUID recipientId;
}
