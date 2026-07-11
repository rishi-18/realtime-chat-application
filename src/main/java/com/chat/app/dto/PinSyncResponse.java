package com.chat.app.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PinSyncResponse {
    private UUID messageId;
    private UUID roomId;
    private boolean pinned;
    private String pinnedByUsername;
    private String action; // "PIN" / "UNPIN"
}
