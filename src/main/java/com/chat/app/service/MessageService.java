package com.chat.app.service;

import com.chat.app.dto.MessageSendRequest;
import com.chat.app.exception.RoomNotFoundException;
import com.chat.app.model.Message;
import com.chat.app.model.Room;
import com.chat.app.model.User;
import com.chat.app.repository.MessageRepository;
import com.chat.app.repository.RoomMemberRepository;
import com.chat.app.repository.RoomRepository;
import com.chat.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;

    @Transactional
    public Message saveMessage(MessageSendRequest request, UUID senderId) {
        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new RoomNotFoundException("Room not found with id: " + request.getRoomId()));

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + senderId));

        if (!roomMemberRepository.existsByIdRoomIdAndIdUserId(room.getId(), sender.getId())) {
            throw new AccessDeniedException("Cannot send message. You are not a member of this room.");
        }

        Message message = Message.builder()
                .room(room)
                .sender(sender)
                .content(request.getContent())
                .build();

        return messageRepository.save(message);
    }

    @Transactional(readOnly = true)
    public Page<Message> getMessageHistory(UUID roomId, UUID userId, int page, int size) {
        if (!roomRepository.existsById(roomId)) {
            throw new RoomNotFoundException("Room not found with id: " + roomId);
        }

        if (!roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)) {
            throw new AccessDeniedException("Cannot access history. You are not a member of this room.");
        }

        // Sort messages by newest first
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return messageRepository.findByRoomId(roomId, pageRequest);
    }
}
