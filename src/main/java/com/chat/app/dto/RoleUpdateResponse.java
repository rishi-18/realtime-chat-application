package com.chat.app.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleUpdateResponse {
    private UUID roomId;
    private UUID userId;
    private String role;
}
