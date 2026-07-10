package com.chat.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class RoomMemberId implements Serializable {

    @Column(name = "room_id", columnDefinition = "UUID")
    private UUID roomId;

    @Column(name = "user_id", columnDefinition = "UUID")
    private UUID userId;
}
