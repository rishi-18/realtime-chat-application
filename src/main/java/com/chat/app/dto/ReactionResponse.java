package com.chat.app.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReactionResponse {
    private UUID userId;
    private String username;
    private String emoji;
}
