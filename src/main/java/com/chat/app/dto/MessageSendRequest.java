package com.chat.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageSendRequest {

    @NotNull(message = "Room ID is required")
    private UUID roomId;

    @Size(max = 4000, message = "Message content cannot exceed 4000 characters")
    private String content;

    private java.util.List<AttachmentRequest> attachments;
}
