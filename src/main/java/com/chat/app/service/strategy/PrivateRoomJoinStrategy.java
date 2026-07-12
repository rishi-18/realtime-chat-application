package com.chat.app.service.strategy;

import com.chat.app.model.Room;
import com.chat.app.model.RoomMember;
import com.chat.app.model.RoomMemberId;
import com.chat.app.model.RoomRole;
import com.chat.app.model.RoomType;
import com.chat.app.model.RoomInvite;
import com.chat.app.model.User;
import com.chat.app.repository.RoomMemberRepository;
import com.chat.app.repository.RoomInviteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class PrivateRoomJoinStrategy implements RoomJoinStrategy {

    private final RoomMemberRepository roomMemberRepository;
    private final RoomInviteRepository roomInviteRepository;

    @Override
    public boolean supports(RoomType roomType) {
        return roomType == RoomType.PRIVATE_GROUP;
    }

    @Override
    public void join(Room room, User user, String inviteCode) {
        if (inviteCode == null || inviteCode.trim().isEmpty()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Cannot join a private room directly. An invite code is required."
            );
        }

        if (roomMemberRepository.existsByIdRoomIdAndIdUserId(room.getId(), user.getId())) {
            throw new IllegalArgumentException("You are already a member of this room.");
        }

        RoomInvite invite = roomInviteRepository.findByCode(inviteCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or non-existent invite code."));

        if (!invite.getRoom().getId().equals(room.getId())) {
            throw new IllegalArgumentException("Invite code does not match the target room.");
        }

        if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("The invite link has expired.");
        }

        // Atomic check and increment
        int affectedRows = roomInviteRepository.incrementUsesAtomic(invite.getId());
        if (affectedRows == 0) {
            throw new IllegalArgumentException("The invite link has reached its maximum usage limit.");
        }

        RoomMemberId membershipId = RoomMemberId.builder()
                .roomId(room.getId())
                .userId(user.getId())
                .build();

        RoomMember membership = RoomMember.builder()
                .id(membershipId)
                .room(room)
                .user(user)
                .role(RoomRole.MEMBER)
                .joinedAt(Instant.now())
                .build();

        roomMemberRepository.save(membership);
    }
}
