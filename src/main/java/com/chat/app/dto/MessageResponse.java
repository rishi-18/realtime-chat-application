package com.chat.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {

    private UUID id;
    private UUID roomId;
    private UUID senderId;
    private String senderUsername;
    private String content;
    private java.util.List<AttachmentResponse> attachments;
    private java.util.List<ReactionResponse> reactions;
    private java.util.List<String> mentionedUsernames;
    private boolean isDeleted;
    private Instant timestamp;
    private Instant updatedAt;
}
