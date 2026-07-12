package com.chat.app.service;

import com.chat.app.dto.MessageResponse;
import com.chat.app.dto.PinSyncResponse;
import com.chat.app.dto.PinToggleResponse;
import com.chat.app.exception.RoomNotFoundException;
import com.chat.app.mapper.MessageMapper;
import com.chat.app.model.*;
import com.chat.app.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessagePinService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final PinnedMessageRepository pinnedMessageRepository;
    private final MessageReactionRepository messageReactionRepository;
    private final MessageMentionRepository messageMentionRepository;
    private final MessageMapper messageMapper;
    
    @Lazy
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public PinToggleResponse togglePinMessage(UUID messageId, UUID userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found with id: " + messageId));

        if (message.isDeleted()) {
            throw new IllegalArgumentException("Cannot pin a soft-deleted message.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        UUID roomId = message.getRoom().getId();
        if (!roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)) {
            throw new AccessDeniedException("Cannot pin message. You are not a member of this room.");
        }

        Optional<PinnedMessage> existingPin = 
                pinnedMessageRepository.findByRoomIdAndMessageId(roomId, messageId);

        boolean pinned;
        String action;
        Instant pinnedAt;

        if (existingPin.isPresent()) {
            pinnedMessageRepository.delete(existingPin.get());
            pinned = false;
            action = "UNPIN";
            pinnedAt = null;
        } else {
            PinnedMessage pin = PinnedMessage.builder()
                    .message(message)
                    .room(message.getRoom())
                    .pinnedBy(user)
                    .build();
            pinnedMessageRepository.save(pin);
            pinned = true;
            action = "PIN";
            pinnedAt = Instant.now();
        }

        // Broadcast WebSocket sync event
        PinSyncResponse syncPayload = PinSyncResponse.builder()
                .messageId(messageId)
                .roomId(roomId)
                .pinned(pinned)
                .pinnedByUsername(user.getUsername())
                .action(action)
                .build();
        messagingTemplate.convertAndSend("/topic/room." + roomId, syncPayload);

        return PinToggleResponse.builder()
                .messageId(messageId)
                .roomId(roomId)
                .pinned(pinned)
                .pinnedByUsername(user.getUsername())
                .pinnedAt(pinnedAt)
                .build();
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getPinnedMessages(UUID roomId, UUID userId) {
        if (!roomRepository.existsById(roomId)) {
            throw new RoomNotFoundException("Room not found with id: " + roomId);
        }

        if (!roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)) {
            throw new AccessDeniedException("Cannot fetch pinned messages. You are not a member of this room.");
        }

        List<PinnedMessage> pins = pinnedMessageRepository.findByRoomId(roomId);
        if (pins == null || pins.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> messageIds = pins.stream()
                .map(p -> p.getMessage().getId())
                .collect(Collectors.toList());

        Map<UUID, List<MessageReaction>> reactionsMap = new HashMap<>();
        Map<UUID, List<MessageMention>> mentionsMap = new HashMap<>();
        
        List<MessageReaction> reactions = messageReactionRepository.findByMessageIdIn(messageIds);
        if (reactions != null) {
            reactionsMap = reactions.stream()
                    .collect(Collectors.groupingBy(r -> r.getMessage().getId()));
        }

        List<MessageMention> mentions = messageMentionRepository.findByMessageIdIn(messageIds);
        if (mentions != null) {
            mentionsMap = mentions.stream()
                    .collect(Collectors.groupingBy(m -> m.getMessage().getId()));
        }

        final Map<UUID, List<MessageReaction>> finalReactionsMap = reactionsMap;
        final Map<UUID, List<MessageMention>> finalMentionsMap = mentionsMap;

        return pins.stream()
                .map(pin -> messageMapper.mapToResponse(
                        pin.getMessage(),
                        finalReactionsMap.getOrDefault(pin.getMessage().getId(), Collections.emptyList()),
                        finalMentionsMap.getOrDefault(pin.getMessage().getId(), Collections.emptyList()),
                        true
                ))
                .collect(Collectors.toList());
    }
}
