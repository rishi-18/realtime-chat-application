package com.chat.app.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_blocks", uniqueConstraints = {
    @UniqueConstraint(name = "unique_user_block", columnNames = {"user_id", "blocked_user_id"})
}, indexes = {
    @Index(name = "idx_user_blocks_user", columnList = "user_id"),
    @Index(name = "idx_user_blocks_blocked", columnList = "blocked_user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_user_id", nullable = false)
    private User blockedUser;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
