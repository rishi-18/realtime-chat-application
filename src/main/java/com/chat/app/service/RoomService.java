package com.chat.app.service;

import com.chat.app.dto.RoomCreateRequest;
import com.chat.app.exception.RoomAlreadyExistsException;
import com.chat.app.exception.RoomNotFoundException;
import com.chat.app.model.Room;
import com.chat.app.model.RoomMember;
import com.chat.app.model.RoomMemberId;
import com.chat.app.model.Message;
import com.chat.app.model.User;
import com.chat.app.repository.MessageRepository;
import com.chat.app.repository.RoomMemberRepository;
import com.chat.app.repository.RoomRepository;
import com.chat.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    @Transactional
    public Room createOrGetDirectMessageRoom(UUID creatorId, UUID recipientId) {
        if (creatorId.equals(recipientId)) {
            throw new IllegalArgumentException("Cannot start a direct message room with yourself.");
        }

        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + creatorId));
        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new IllegalArgumentException("Recipient user not found with id: " + recipientId));

        String roomName = generateDirectMessageRoomName(creatorId, recipientId);

        java.util.Optional<Room> existingRoom = roomRepository.findByName(roomName);
        if (existingRoom.isPresent()) {
            return existingRoom.get();
        }

        Room room = Room.builder()
                .name(roomName)
                .description("Direct message room between " + creator.getUsername() + " and " + recipient.getUsername())
                .roomType(com.chat.app.model.RoomType.DIRECT_MESSAGE)
                .createdBy(creator)
                .build();

        Room savedRoom = roomRepository.save(room);

        RoomMemberId creatorMembershipId = RoomMemberId.builder()
                .roomId(savedRoom.getId())
                .userId(creator.getId())
                .build();
        RoomMember creatorMembership = RoomMember.builder()
                .id(creatorMembershipId)
                .room(savedRoom)
                .user(creator)
                .role(com.chat.app.model.RoomRole.MEMBER)
                .build();
        roomMemberRepository.save(creatorMembership);

        RoomMemberId recipientMembershipId = RoomMemberId.builder()
                .roomId(savedRoom.getId())
                .userId(recipient.getId())
                .build();
        RoomMember recipientMembership = RoomMember.builder()
                .id(recipientMembershipId)
                .room(savedRoom)
                .user(recipient)
                .role(com.chat.app.model.RoomRole.MEMBER)
                .build();
        roomMemberRepository.save(recipientMembership);

        return savedRoom;
    }

    private String generateDirectMessageRoomName(UUID id1, UUID id2) {
        String s1 = id1.toString();
        String s2 = id2.toString();
        String combined = s1.compareTo(s2) < 0 ? s1 + ":" + s2 : s2 + ":" + s1;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(combined.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return "dm-" + sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 digest algorithm not available", e);
        }
    }

    @Transactional
    public Room createRoom(RoomCreateRequest request, UUID creatorId) {
        if (roomRepository.existsByName(request.getName())) {
            throw new RoomAlreadyExistsException("A room with the name '" + request.getName() + "' already exists.");
        }

        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + creatorId));

        Room room = Room.builder()
                .name(request.getName())
                .description(request.getDescription())
                .createdBy(creator)
                .build();

        Room savedRoom = roomRepository.save(room);

        // Auto-join the creator to the room
        RoomMemberId membershipId = RoomMemberId.builder()
                .roomId(savedRoom.getId())
                .userId(creator.getId())
                .build();

        RoomMember membership = RoomMember.builder()
                .id(membershipId)
                .room(savedRoom)
                .user(creator)
                .role(com.chat.app.model.RoomRole.OWNER)
                .build();

        roomMemberRepository.save(membership);

        return savedRoom;
    }

    @Transactional(readOnly = true)
    public List<Room> getAllRooms() {
        return roomRepository.findByRoomType(com.chat.app.model.RoomType.PUBLIC_GROUP);
    }

    @Transactional
    public void joinRoom(UUID roomId, UUID userId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found with id: " + roomId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        if (roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)) {
            throw new IllegalArgumentException("You are already a member of this room.");
        }

        RoomMemberId membershipId = RoomMemberId.builder()
                .roomId(roomId)
                .userId(userId)
                .build();

        RoomMember membership = RoomMember.builder()
                .id(membershipId)
                .room(room)
                .user(user)
                .role(com.chat.app.model.RoomRole.MEMBER)
                .build();

        roomMemberRepository.save(membership);
    }

    @Transactional(readOnly = true)
    public boolean isUserMemberOfRoom(UUID roomId, UUID userId) {
        return roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId);
    }

    @Transactional
    public Room createOrGetDmRoom(UUID creatorId, UUID recipientId) {
        if (creatorId.equals(recipientId)) {
            throw new IllegalArgumentException("Cannot start a direct message with yourself.");
        }

        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + creatorId));
        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new IllegalArgumentException("Recipient user not found with id: " + recipientId));

        // Check if DM room already exists
        java.util.Optional<UUID> existingRoomId = roomMemberRepository.findDmRoomBetweenUsers(creatorId, recipientId);
        if (existingRoomId.isPresent()) {
            return roomRepository.findById(existingRoomId.get())
                    .orElseThrow(() -> new RoomNotFoundException("Existing DM room not found in repository."));
        }

        // Generate unique deterministic name (MD5 hashed to fit 50-character schema limit)
        String id1 = creatorId.toString();
        String id2 = recipientId.toString();
        String combined = id1.compareTo(id2) < 0 ? id1 + ":" + id2 : id2 + ":" + id1;
        String dmName;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(combined.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            dmName = "dm-" + sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 digest algorithm not available", e);
        }

        Room room = Room.builder()
                .name(dmName)
                .description("Direct Message")
                .createdBy(creator)
                .roomType(com.chat.app.model.RoomType.DIRECT_MESSAGE)
                .build();

        Room savedRoom = roomRepository.save(room);

        // Join both users to the DM room
        joinRoomInternal(savedRoom, creator);
        joinRoomInternal(savedRoom, recipient);

        return savedRoom;
    }

    private void joinRoomInternal(Room room, User user) {
        RoomMemberId membershipId = RoomMemberId.builder()
                .roomId(room.getId())
                .userId(user.getId())
                .build();

        RoomMember membership = RoomMember.builder()
                .id(membershipId)
                .room(room)
                .user(user)
                .build();

        roomMemberRepository.save(membership);
    }

    @Transactional
    public void updateLastReadMessage(UUID userId, UUID roomId, UUID messageId) {
        RoomMemberId membershipId = RoomMemberId.builder()
                .roomId(roomId)
                .userId(userId)
                .build();

        RoomMember member = roomMemberRepository.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of the room."));

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found with id: " + messageId));

        if (!message.getRoom().getId().equals(roomId)) {
            throw new IllegalArgumentException("Message does not belong to the specified room.");
        }

        // Out of order safety check
        if (member.getLastReadMessage() != null) {
            if (message.getCreatedAt().isBefore(member.getLastReadMessage().getCreatedAt())) {
                // Ignore update if it points to an older message
                return;
            }
        }

        member.setLastReadMessage(message);
        roomMemberRepository.save(member);
    }

    @Transactional
    public com.chat.app.dto.RoleUpdateResponse updateMemberRole(UUID roomId, UUID targetUserId, com.chat.app.model.RoomRole newRole, UUID requesterId) {
        RoomMemberId requesterMembershipId = RoomMemberId.builder()
                .roomId(roomId)
                .userId(requesterId)
                .build();

        RoomMember requester = roomMemberRepository.findById(requesterMembershipId)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Requester is not a member of this room."));

        if (requester.getRole() != com.chat.app.model.RoomRole.OWNER) {
            throw new org.springframework.security.access.AccessDeniedException("Only room owners can change roles.");
        }

        RoomMemberId targetMembershipId = RoomMemberId.builder()
                .roomId(roomId)
                .userId(targetUserId)
                .build();

        RoomMember target = roomMemberRepository.findById(targetMembershipId)
                .orElseThrow(() -> new IllegalArgumentException("Target user is not a member of this room."));

        // Single owner enforcement
        if (targetUserId.equals(requesterId) && newRole != com.chat.app.model.RoomRole.OWNER) {
            throw new IllegalArgumentException("Room owners cannot demote themselves.");
        }

        target.setRole(newRole);
        RoomMember saved = roomMemberRepository.save(target);

        return com.chat.app.dto.RoleUpdateResponse.builder()
                .roomId(roomId)
                .userId(targetUserId)
                .role(saved.getRole().name())
                .build();
    }

    @Transactional
    public void kickMember(UUID roomId, UUID targetUserId, UUID requesterId) {
        RoomMemberId requesterMembershipId = RoomMemberId.builder()
                .roomId(roomId)
                .userId(requesterId)
                .build();

        RoomMember requester = roomMemberRepository.findById(requesterMembershipId)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Requester is not a member of this room."));

        if (requester.getRole() != com.chat.app.model.RoomRole.OWNER && requester.getRole() != com.chat.app.model.RoomRole.MODERATOR) {
            throw new org.springframework.security.access.AccessDeniedException("Only room owners and moderators can kick members.");
        }

        RoomMemberId targetMembershipId = RoomMemberId.builder()
                .roomId(roomId)
                .userId(targetUserId)
                .build();

        RoomMember target = roomMemberRepository.findById(targetMembershipId)
                .orElseThrow(() -> new IllegalArgumentException("Target user is not a member of this room."));

        // Moderator cannot kick the owner
        if (target.getRole() == com.chat.app.model.RoomRole.OWNER) {
            throw new org.springframework.security.access.AccessDeniedException("Moderators cannot kick the room owner.");
        }

        // Owner cannot kick themselves
        if (targetUserId.equals(requesterId)) {
            throw new IllegalArgumentException("Room owners cannot kick themselves.");
        }

        roomMemberRepository.delete(target);
    }
}
