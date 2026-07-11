package com.chat.app.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReactionSyncResponse {
    private UUID messageId;
    private UUID roomId;
    private String emoji;
    private String action; // "ADDED" or "REMOVED"
    private UUID userId;
    private String username;
}
