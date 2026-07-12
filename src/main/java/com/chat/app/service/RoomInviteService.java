package com.chat.app.service;

import com.chat.app.dto.RoomInviteCreateRequest;
import com.chat.app.dto.RoomInviteResponse;
import com.chat.app.model.*;
import com.chat.app.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoomInviteService {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int CODE_LENGTH = 8;
    private final SecureRandom secureRandom = new SecureRandom();

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomInviteRepository roomInviteRepository;
    private final UserRepository userRepository;

    @Transactional
    public RoomInviteResponse createInvite(UUID roomId, RoomInviteCreateRequest request, UUID creatorId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found with id: " + roomId));

        RoomMember membership = roomMemberRepository.findById(new RoomMemberId(roomId, creatorId))
                .orElseThrow(() -> new AccessDeniedException("You must be a member of the room to create invites."));

        if (membership.getRole() != RoomRole.OWNER && membership.getRole() != RoomRole.MODERATOR) {
            throw new AccessDeniedException("Only room Owners or Moderators can generate invite links.");
        }

        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new IllegalArgumentException("Creator not found with id: " + creatorId));

        String code = generateUniqueCode();
        Instant expiresAt = null;
        if (request.getExpirationSeconds() != null && request.getExpirationSeconds() > 0) {
            expiresAt = Instant.now().plusSeconds(request.getExpirationSeconds());
        }

        RoomInvite invite = RoomInvite.builder()
                .room(room)
                .code(code)
                .createdBy(creator)
                .maxUses(request.getMaxUses())
                .uses(0)
                .expiresAt(expiresAt)
                .createdAt(Instant.now())
                .build();

        RoomInvite saved = roomInviteRepository.save(invite);
        log.info("Successfully generated room invite code: {} for room: {}", code, roomId);

        return mapToResponse(saved);
    }

    @Transactional
    public void joinRoomByInvite(String code, UUID userId) {
        RoomInvite invite = roomInviteRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or non-existent invite code."));

        if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("The invite link has expired.");
        }

        UUID roomId = invite.getRoom().getId();
        Optional<RoomMember> existing = roomMemberRepository.findById(new RoomMemberId(roomId, userId));
        if (existing.isPresent()) {
            throw new IllegalArgumentException("You are already a member of this room.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        // Atomic check and increment
        int affectedRows = roomInviteRepository.incrementUsesAtomic(invite.getId());
        if (affectedRows == 0) {
            throw new IllegalArgumentException("The invite link has reached its maximum usage limit.");
        }

        RoomMemberId membershipId = new RoomMemberId(roomId, userId);
        RoomMember membership = RoomMember.builder()
                .id(membershipId)
                .room(invite.getRoom())
                .user(user)
                .role(RoomRole.MEMBER)
                .joinedAt(Instant.now())
                .build();

        roomMemberRepository.save(membership);
        log.info("User {} joined room {} successfully via invite code {}", userId, roomId, code);
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CHARACTERS.charAt(secureRandom.nextInt(CHARACTERS.length())));
            }
            String code = sb.toString();
            if (!roomInviteRepository.findByCode(code).isPresent()) {
                return code;
            }
        }
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private RoomInviteResponse mapToResponse(RoomInvite invite) {
        return RoomInviteResponse.builder()
                .id(invite.getId())
                .roomId(invite.getRoom().getId())
                .code(invite.getCode())
                .maxUses(invite.getMaxUses())
                .uses(invite.getUses())
                .expiresAt(invite.getExpiresAt())
                .build();
    }
}
