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

        // Generate unique deterministic name
        String id1 = creatorId.toString();
        String id2 = recipientId.toString();
        String dmName = id1.compareTo(id2) < 0 ? "dm:" + id1 + ":" + id2 : "dm:" + id2 + ":" + id1;

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
}
