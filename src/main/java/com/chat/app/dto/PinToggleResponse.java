package com.chat.app.dto;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PinToggleResponse {
    private UUID messageId;
    private UUID roomId;
    private boolean pinned;
    private String pinnedByUsername;
    private Instant pinnedAt;
}
