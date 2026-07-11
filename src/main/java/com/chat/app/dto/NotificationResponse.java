package com.chat.app.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {
    private UUID messageId;
    private UUID roomId;
    private String senderUsername;
    private String type; // "MENTION"
    private String snippet;
}
