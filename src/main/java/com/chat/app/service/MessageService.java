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
public class MessageService {

    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final com.chat.app.repository.MessageReactionRepository messageReactionRepository;
    private final com.chat.app.repository.MessageMentionRepository messageMentionRepository;
    private final com.chat.app.repository.PinnedMessageRepository pinnedMessageRepository;
    private final com.chat.app.repository.MessageRevisionRepository messageRevisionRepository;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    private final com.chat.app.service.UserBlockService userBlockService;
    private final com.chat.app.mapper.MessageMapper messageMapper;

    public MessageService(
            MessageRepository messageRepository,
            RoomRepository roomRepository,
            RoomMemberRepository roomMemberRepository,
            UserRepository userRepository,
            com.chat.app.repository.MessageReactionRepository messageReactionRepository,
            com.chat.app.repository.MessageMentionRepository messageMentionRepository,
            com.chat.app.repository.PinnedMessageRepository pinnedMessageRepository,
            com.chat.app.repository.MessageRevisionRepository messageRevisionRepository,
            @org.springframework.context.annotation.Lazy org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate,
            com.chat.app.service.UserBlockService userBlockService,
            com.chat.app.mapper.MessageMapper messageMapper) {
        this.messageRepository = messageRepository;
        this.roomRepository = roomRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.userRepository = userRepository;
        this.messageReactionRepository = messageReactionRepository;
        this.messageMentionRepository = messageMentionRepository;
        this.pinnedMessageRepository = pinnedMessageRepository;
        this.messageRevisionRepository = messageRevisionRepository;
        this.messagingTemplate = messagingTemplate;
        this.userBlockService = userBlockService;
        this.messageMapper = messageMapper;
    }

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

        if (room.getRoomType() == com.chat.app.model.RoomType.DIRECT_MESSAGE) {
            java.util.List<com.chat.app.model.RoomMember> members = roomMemberRepository.findByIdRoomId(room.getId());
            for (com.chat.app.model.RoomMember member : members) {
                if (!member.getUser().getId().equals(senderId)) {
                    UUID otherUserId = member.getUser().getId();
                    if (userBlockService.isBlockedSymmetrically(senderId, otherUserId)) {
                        throw new AccessDeniedException("Cannot send message. You have blocked this user or they have blocked you.");
                    }
                }
            }
        }

        Message parentMessage = null;
        if (request.getParentMessageId() != null) {
            parentMessage = messageRepository.findById(request.getParentMessageId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent message not found with id: " + request.getParentMessageId()));
            
            if (!parentMessage.getRoom().getId().equals(room.getId())) {
                throw new IllegalArgumentException("Parent message must belong to the same chat room.");
            }
        }

        Message message = Message.builder()
                .room(room)
                .sender(sender)
                .content(request.getContent())
                .parentMessage(parentMessage)
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

        Message savedMessage = messageRepository.save(message);

        // Parse and persist mentions if text content exists
        if (savedMessage.getContent() != null && !savedMessage.getContent().trim().isEmpty()) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?<=^|(?<=\\s))@([a-zA-Z0-9_]{3,30})");
            java.util.regex.Matcher matcher = pattern.matcher(savedMessage.getContent());
            java.util.Set<String> extractedUsernames = new java.util.HashSet<>();
            while (matcher.find()) {
                extractedUsernames.add(matcher.group(1));
            }

            for (String username : extractedUsernames) {
                if (username.equals(sender.getUsername())) {
                    continue; // Skip self-mentions
                }
                userRepository.findByUsername(username).ifPresent(targetUser -> {
                    if (roomMemberRepository.existsByIdRoomIdAndIdUserId(room.getId(), targetUser.getId())) {
                        if (userBlockService.isBlockedSymmetrically(sender.getId(), targetUser.getId())) {
                            return;
                        }

                        com.chat.app.model.MessageMention mention = com.chat.app.model.MessageMention.builder()
                                .message(savedMessage)
                                .user(targetUser)
                                .build();
                        messageMentionRepository.save(mention);

                        // Broadcast private notifications sync frame
                        com.chat.app.dto.NotificationResponse notification = com.chat.app.dto.NotificationResponse.builder()
                                .messageId(savedMessage.getId())
                                .roomId(room.getId())
                                .senderUsername(sender.getUsername())
                                .type("MENTION")
                                .snippet(savedMessage.getContent())
                                .build();
                        messagingTemplate.convertAndSendToUser(targetUser.getUsername(), "/queue/notifications", notification);
                    }
                });
            }
        }

        return savedMessage;
    }



    @Transactional(readOnly = true)
    public Page<com.chat.app.dto.MessageResponse> getMessageHistoryResponses(UUID roomId, UUID userId, int page, int size) {
        Page<Message> messages = getMessageHistory(roomId, userId, page, size);
        
        java.util.List<UUID> messageIds = messages.getContent().stream()
                .map(Message::getId)
                .collect(java.util.stream.Collectors.toList());

        java.util.Map<UUID, java.util.List<com.chat.app.model.MessageReaction>> reactionsMap = new java.util.HashMap<>();
        java.util.Map<UUID, java.util.List<com.chat.app.model.MessageMention>> mentionsMap = new java.util.HashMap<>();
        java.util.Set<UUID> pinnedMessageIds = new java.util.HashSet<>();
        if (!messageIds.isEmpty()) {
            java.util.List<com.chat.app.model.MessageReaction> reactions = messageReactionRepository.findByMessageIdIn(messageIds);
            if (reactions != null) {
                reactionsMap = reactions.stream()
                        .collect(java.util.stream.Collectors.groupingBy(r -> r.getMessage().getId()));
            }

            java.util.List<com.chat.app.model.MessageMention> mentions = messageMentionRepository.findByMessageIdIn(messageIds);
            if (mentions != null) {
                mentionsMap = mentions.stream()
                        .collect(java.util.stream.Collectors.groupingBy(m -> m.getMessage().getId()));
            }

            java.util.List<com.chat.app.model.PinnedMessage> pins = pinnedMessageRepository.findByMessageIdIn(messageIds);
            if (pins != null) {
                pinnedMessageIds = pins.stream()
                        .map(p -> p.getMessage().getId())
                        .collect(java.util.stream.Collectors.toSet());
            }
        }

        final java.util.Map<UUID, java.util.List<com.chat.app.model.MessageReaction>> finalReactionsMap = reactionsMap;
        final java.util.Map<UUID, java.util.List<com.chat.app.model.MessageMention>> finalMentionsMap = mentionsMap;
        final java.util.Set<UUID> finalPinnedMessageIds = pinnedMessageIds;

        return messages.map(message -> messageMapper.mapToResponse(
                message, 
                finalReactionsMap.getOrDefault(message.getId(), java.util.Collections.emptyList()),
                finalMentionsMap.getOrDefault(message.getId(), java.util.Collections.emptyList()),
                finalPinnedMessageIds.contains(message.getId())
        ));
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
        
        java.util.List<UUID> blockedIds = new java.util.ArrayList<>();
        blockedIds.addAll(userBlockService.getBlockedUserIds(userId));
        blockedIds.addAll(userBlockService.getUsersWhoBlockedMeIds(userId));

        if (!blockedIds.isEmpty()) {
            return messageRepository.findByRoomIdAndSenderIdNotIn(roomId, blockedIds, pageRequest);
        }
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

        if (request.getContent() != null && !request.getContent().equals(message.getContent())) {
            com.chat.app.model.MessageRevision revision = com.chat.app.model.MessageRevision.builder()
                    .message(message)
                    .oldContent(message.getContent() != null ? message.getContent() : "")
                    .editedAt(java.time.Instant.now())
                    .build();
            messageRevisionRepository.save(revision);
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

        boolean isAuthorized = false;
        if (message.getSender() != null && message.getSender().getId().equals(userId)) {
            isAuthorized = true;
        } else {
            com.chat.app.model.RoomMemberId membershipId = com.chat.app.model.RoomMemberId.builder()
                    .roomId(message.getRoom().getId())
                    .userId(userId)
                    .build();
            java.util.Optional<com.chat.app.model.RoomMember> memberOpt = roomMemberRepository.findById(membershipId);
            if (memberOpt.isPresent()) {
                com.chat.app.model.RoomRole role = memberOpt.get().getRole();
                if (role == com.chat.app.model.RoomRole.OWNER || role == com.chat.app.model.RoomRole.MODERATOR) {
                    isAuthorized = true;
                }
            }
        }

        if (!isAuthorized) {
            throw new org.springframework.security.access.AccessDeniedException("You are not authorized to delete this message.");
        }

        message.setDeleted(true);
        message.setContent(null);
        message.getAttachments().clear();
        message.setUpdatedAt(java.time.Instant.now());

        if (message.getRoom() != null) {
            pinnedMessageRepository.findByRoomIdAndMessageId(message.getRoom().getId(), messageId)
                    .ifPresent(pinnedMessageRepository::delete);
        }

        return messageRepository.save(message);
    }



    @Transactional(readOnly = true)
    public Page<com.chat.app.dto.MessageResponse> searchRoomMessages(UUID roomId, UUID userId, String query, int page, int size) {
        if (!roomRepository.existsById(roomId)) {
            throw new RoomNotFoundException("Room not found with id: " + roomId);
        }

        if (!roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)) {
            throw new AccessDeniedException("Cannot search room messages. You are not a member of this room.");
        }

        PageRequest pageable = PageRequest.of(page, size);
        Page<Message> messages = messageRepository.searchRoomMessages(roomId, query, pageable);

        java.util.List<UUID> messageIds = messages.getContent().stream()
                .map(Message::getId)
                .collect(java.util.stream.Collectors.toList());

        java.util.Map<UUID, java.util.List<com.chat.app.model.MessageReaction>> reactionsMap = new java.util.HashMap<>();
        java.util.Map<UUID, java.util.List<com.chat.app.model.MessageMention>> mentionsMap = new java.util.HashMap<>();
        java.util.Set<UUID> pinnedMessageIds = new java.util.HashSet<>();
        if (!messageIds.isEmpty()) {
            java.util.List<com.chat.app.model.MessageReaction> reactions = messageReactionRepository.findByMessageIdIn(messageIds);
            if (reactions != null) {
                reactionsMap = reactions.stream()
                        .collect(java.util.stream.Collectors.groupingBy(r -> r.getMessage().getId()));
            }

            java.util.List<com.chat.app.model.MessageMention> mentions = messageMentionRepository.findByMessageIdIn(messageIds);
            if (mentions != null) {
                mentionsMap = mentions.stream()
                        .collect(java.util.stream.Collectors.groupingBy(m -> m.getMessage().getId()));
            }

            java.util.List<com.chat.app.model.PinnedMessage> pins = pinnedMessageRepository.findByMessageIdIn(messageIds);
            if (pins != null) {
                pinnedMessageIds = pins.stream()
                        .map(p -> p.getMessage().getId())
                        .collect(java.util.stream.Collectors.toSet());
            }
        }

        final java.util.Map<UUID, java.util.List<com.chat.app.model.MessageReaction>> finalReactionsMap = reactionsMap;
        final java.util.Map<UUID, java.util.List<com.chat.app.model.MessageMention>> finalMentionsMap = mentionsMap;
        final java.util.Set<UUID> finalPinnedMessageIds = pinnedMessageIds;

        return messages.map(message -> messageMapper.mapToResponse(
                message, 
                finalReactionsMap.getOrDefault(message.getId(), java.util.Collections.emptyList()),
                finalMentionsMap.getOrDefault(message.getId(), java.util.Collections.emptyList()),
                finalPinnedMessageIds.contains(message.getId())
        ));
    }



    @Transactional(readOnly = true)
    public java.util.List<com.chat.app.dto.MessageResponse> getThreadReplies(UUID parentMessageId, UUID userId) {
        Message parent = messageRepository.findById(parentMessageId)
                .orElseThrow(() -> new IllegalArgumentException("Parent message not found with id: " + parentMessageId));

        UUID roomId = parent.getRoom().getId();
        if (!roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)) {
            throw new AccessDeniedException("Cannot fetch thread. You are not a member of this room.");
        }

        java.util.List<Message> replies = messageRepository.findByParentMessageIdOrderByCreatedAtAsc(parentMessageId);
        if (replies == null || replies.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        java.util.List<UUID> replyIds = replies.stream()
                .map(Message::getId)
                .collect(java.util.stream.Collectors.toList());

        java.util.Map<UUID, java.util.List<com.chat.app.model.MessageReaction>> reactionsMap = new java.util.HashMap<>();
        java.util.Map<UUID, java.util.List<com.chat.app.model.MessageMention>> mentionsMap = new java.util.HashMap<>();
        java.util.Set<UUID> pinnedMessageIds = new java.util.HashSet<>();

        java.util.List<com.chat.app.model.MessageReaction> reactions = messageReactionRepository.findByMessageIdIn(replyIds);
        if (reactions != null) {
            reactionsMap = reactions.stream()
                    .collect(java.util.stream.Collectors.groupingBy(r -> r.getMessage().getId()));
        }

        java.util.List<com.chat.app.model.MessageMention> mentions = messageMentionRepository.findByMessageIdIn(replyIds);
        if (mentions != null) {
            mentionsMap = mentions.stream()
                    .collect(java.util.stream.Collectors.groupingBy(m -> m.getMessage().getId()));
        }

        java.util.List<com.chat.app.model.PinnedMessage> pins = pinnedMessageRepository.findByMessageIdIn(replyIds);
        if (pins != null) {
            pinnedMessageIds = pins.stream()
                    .map(p -> p.getMessage().getId())
                    .collect(java.util.stream.Collectors.toSet());
        }

        final java.util.Map<UUID, java.util.List<com.chat.app.model.MessageReaction>> finalReactionsMap = reactionsMap;
        final java.util.Map<UUID, java.util.List<com.chat.app.model.MessageMention>> finalMentionsMap = mentionsMap;
        final java.util.Set<UUID> finalPinnedMessageIds = pinnedMessageIds;

        return replies.stream()
                .map(reply -> messageMapper.mapToResponse(
                        reply,
                        finalReactionsMap.getOrDefault(reply.getId(), java.util.Collections.emptyList()),
                        finalMentionsMap.getOrDefault(reply.getId(), java.util.Collections.emptyList()),
                        finalPinnedMessageIds.contains(reply.getId())
                ))
                .collect(java.util.stream.Collectors.toList());
    }


}
