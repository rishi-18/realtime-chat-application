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
public class RoomInviteResponse {

    private UUID id;
    private UUID roomId;
    private String code;
    private Integer maxUses;
    private int uses;
    private Instant expiresAt;
}
