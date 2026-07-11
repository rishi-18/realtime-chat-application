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
    private final com.chat.app.repository.MessageReactionRepository messageReactionRepository;

    @Transactional
    public Message saveMessage(MessageSendRequest request, UUID senderId) {
        if ((request.getContent() == null || request.getContent().trim().isEmpty()) &&
                (request.getAttachments() == null || request.getAttachments().isEmpty())) {
            throw new IllegalArgumentException("Message must contain either text content or attachments.");
        }

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

        if (request.getAttachments() != null && !request.getAttachments().isEmpty()) {
            for (com.chat.app.dto.AttachmentRequest attReq : request.getAttachments()) {
                com.chat.app.model.MessageAttachment attachment = com.chat.app.model.MessageAttachment.builder()
                        .message(message)
                        .fileName(attReq.getFileName())
                        .fileUrl(attReq.getFileUrl())
                        .fileType(attReq.getFileType())
                        .fileSize(attReq.getFileSize())
                        .build();
                message.getAttachments().add(attachment);
            }
        }

        return messageRepository.save(message);
    }

    public com.chat.app.dto.MessageResponse mapToResponse(Message message) {
        return mapToResponse(message, java.util.Collections.emptyList());
    }

    public com.chat.app.dto.MessageResponse mapToResponse(Message message, java.util.List<com.chat.app.model.MessageReaction> reactions) {
        java.util.List<com.chat.app.dto.AttachmentResponse> attachments = null;
        if (message.getAttachments() != null) {
            attachments = message.getAttachments().stream()
                    .map(att -> com.chat.app.dto.AttachmentResponse.builder()
                            .id(att.getId())
                            .fileName(att.getFileName())
                            .fileUrl(att.getFileUrl())
                            .fileType(att.getFileType())
                            .fileSize(att.getFileSize())
                            .build())
                    .collect(java.util.stream.Collectors.toList());
        }

        java.util.List<com.chat.app.dto.ReactionResponse> reactionResponses = null;
        if (reactions != null && !reactions.isEmpty()) {
            reactionResponses = reactions.stream()
                    .map(r -> com.chat.app.dto.ReactionResponse.builder()
                            .userId(r.getUser().getId())
                            .username(r.getUser().getUsername())
                            .emoji(r.getEmoji())
                            .build())
                    .collect(java.util.stream.Collectors.toList());
        }

        return com.chat.app.dto.MessageResponse.builder()
                .id(message.getId())
                .roomId(message.getRoom().getId())
                .senderId(message.getSender() != null ? message.getSender().getId() : null)
                .senderUsername(message.getSender() != null ? message.getSender().getUsername() : "Deleted User")
                .content(message.getContent())
                .attachments(attachments)
                .reactions(reactionResponses)
                .isDeleted(message.isDeleted())
                .timestamp(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public Page<com.chat.app.dto.MessageResponse> getMessageHistoryResponses(UUID roomId, UUID userId, int page, int size) {
        Page<Message> messages = getMessageHistory(roomId, userId, page, size);
        
        java.util.List<UUID> messageIds = messages.getContent().stream()
                .map(Message::getId)
                .collect(java.util.stream.Collectors.toList());

        java.util.Map<UUID, java.util.List<com.chat.app.model.MessageReaction>> reactionsMap = new java.util.HashMap<>();
        if (!messageIds.isEmpty()) {
            java.util.List<com.chat.app.model.MessageReaction> reactions = messageReactionRepository.findByMessageIdIn(messageIds);
            reactionsMap = reactions.stream()
                    .collect(java.util.stream.Collectors.groupingBy(r -> r.getMessage().getId()));
        }

        final java.util.Map<UUID, java.util.List<com.chat.app.model.MessageReaction>> finalReactionsMap = reactionsMap;

        return messages.map(message -> mapToResponse(message, finalReactionsMap.getOrDefault(message.getId(), java.util.Collections.emptyList())));
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

    @Transactional
    public Message editMessage(UUID messageId, com.chat.app.dto.MessageUpdateRequest request, UUID userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found with id: " + messageId));

        if (message.isDeleted()) {
            throw new IllegalArgumentException("Cannot edit a soft-deleted message.");
        }

        if (message.getSender() == null || !message.getSender().getId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("You are not authorized to edit this message.");
        }

        message.setContent(request.getContent());
        message.setUpdatedAt(java.time.Instant.now());

        return messageRepository.save(message);
    }

    @Transactional
    public Message deleteMessage(UUID messageId, UUID userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found with id: " + messageId));

        if (message.isDeleted()) {
            return message;
        }

        if (message.getSender() == null || !message.getSender().getId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("You are not authorized to delete this message.");
        }

        message.setDeleted(true);
        message.setContent(null);
        message.getAttachments().clear();
        message.setUpdatedAt(java.time.Instant.now());

        return messageRepository.save(message);
    }

    @Transactional
    public com.chat.app.dto.ReactionSyncResponse toggleReaction(UUID messageId, String emoji, UUID userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found with id: " + messageId));

        if (message.isDeleted()) {
            throw new IllegalArgumentException("Cannot react to a soft-deleted message.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        // Enforce membership constraint
        if (!roomMemberRepository.existsByIdRoomIdAndIdUserId(message.getRoom().getId(), userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Cannot react to message. You are not a member of this room.");
        }

        java.util.Optional<com.chat.app.model.MessageReaction> existing =
                messageReactionRepository.findByMessageIdAndUserIdAndEmoji(messageId, userId, emoji);

        String action;
        if (existing.isPresent()) {
            messageReactionRepository.delete(existing.get());
            action = "REMOVED";
        } else {
            com.chat.app.model.MessageReaction reaction = com.chat.app.model.MessageReaction.builder()
                    .message(message)
                    .user(user)
                    .emoji(emoji)
                    .build();
            messageReactionRepository.save(reaction);
            action = "ADDED";
        }

        return com.chat.app.dto.ReactionSyncResponse.builder()
                .messageId(messageId)
                .roomId(message.getRoom().getId())
                .emoji(emoji)
                .action(action)
                .userId(userId)
                .username(user.getUsername())
                .build();
    }
}
