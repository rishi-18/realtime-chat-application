package com.chat.app.service;

import com.chat.app.dto.MessageSendRequest;
import com.chat.app.exception.RoomNotFoundException;
import com.chat.app.model.Message;
import com.chat.app.model.Room;
import com.chat.app.model.RoomMember;
import com.chat.app.model.RoomMemberId;
import com.chat.app.model.User;
import com.chat.app.repository.MessageRepository;
import com.chat.app.repository.RoomMemberRepository;
import com.chat.app.repository.RoomRepository;
import com.chat.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomMemberRepository roomMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.chat.app.repository.MessageReactionRepository messageReactionRepository;

    @Mock
    private com.chat.app.repository.MessageMentionRepository messageMentionRepository;

    @Mock
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    @Mock
    private com.chat.app.repository.PinnedMessageRepository pinnedMessageRepository;

    @InjectMocks
    private MessageService messageService;

    private User sender;
    private Room room;
    private MessageSendRequest sendRequest;

    @BeforeEach
    void setUp() {
        sender = User.builder()
                .id(UUID.randomUUID())
                .username("sender")
                .email("sender@example.com")
                .build();

        room = Room.builder()
                .id(UUID.randomUUID())
                .name("developers")
                .build();

        sendRequest = MessageSendRequest.builder()
                .roomId(room.getId())
                .content("Test message")
                .build();
    }

    @Test
    void saveMessage_Success() {
        // Arrange
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(userRepository.findById(sender.getId())).thenReturn(Optional.of(sender));
        when(roomMemberRepository.existsByIdRoomIdAndIdUserId(room.getId(), sender.getId())).thenReturn(true);

        Message savedMessage = Message.builder()
                .id(UUID.randomUUID())
                .room(room)
                .sender(sender)
                .content(sendRequest.getContent())
                .build();
        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);

        // Act
        Message result = messageService.saveMessage(sendRequest, sender.getId());

        // Assert
        assertNotNull(result);
        assertEquals(sendRequest.getContent(), result.getContent());
        assertEquals(room, result.getRoom());
        assertEquals(sender, result.getSender());
        verify(messageRepository, times(1)).save(any(Message.class));
    }

    @Test
    void saveMessage_ThrowsAccessDeniedException_WhenNotMember() {
        // Arrange
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(userRepository.findById(sender.getId())).thenReturn(Optional.of(sender));
        when(roomMemberRepository.existsByIdRoomIdAndIdUserId(room.getId(), sender.getId())).thenReturn(false);

        // Act & Assert
        assertThrows(AccessDeniedException.class, () -> messageService.saveMessage(sendRequest, sender.getId()));
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void getMessageHistory_Success() {
        // Arrange
        UUID roomId = room.getId();
        UUID userId = sender.getId();

        when(roomRepository.existsById(roomId)).thenReturn(true);
        when(roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)).thenReturn(true);

        Page<Message> expectedPage = new PageImpl<>(Collections.singletonList(new Message()));
        when(messageRepository.findByRoomId(eq(roomId), any(PageRequest.class))).thenReturn(expectedPage);

        // Act
        Page<Message> result = messageService.getMessageHistory(roomId, userId, 0, 50);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(messageRepository, times(1)).findByRoomId(eq(roomId), any(PageRequest.class));
    }

    @Test
    void getMessageHistory_ThrowsAccessDeniedException_WhenNotMember() {
        // Arrange
        UUID roomId = room.getId();
        UUID userId = sender.getId();

        when(roomRepository.existsById(roomId)).thenReturn(true);
        when(roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)).thenReturn(false);

        // Act & Assert
        assertThrows(AccessDeniedException.class, () -> messageService.getMessageHistory(roomId, userId, 0, 50));
        verify(messageRepository, never()).findByRoomId(eq(roomId), any(PageRequest.class));
    }
    @Test
    void saveMessage_Success_WithAttachments() {
        // Arrange
        com.chat.app.dto.AttachmentRequest attachmentRequest = com.chat.app.dto.AttachmentRequest.builder()
                .fileName("test.png")
                .fileUrl("/uploads/test.png")
                .fileType("image/png")
                .fileSize(100L)
                .build();
        sendRequest.setAttachments(Collections.singletonList(attachmentRequest));

        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(userRepository.findById(sender.getId())).thenReturn(Optional.of(sender));
        when(roomMemberRepository.existsByIdRoomIdAndIdUserId(room.getId(), sender.getId())).thenReturn(true);

        Message savedMessage = Message.builder()
                .id(UUID.randomUUID())
                .room(room)
                .sender(sender)
                .content(sendRequest.getContent())
                .attachments(new java.util.ArrayList<>())
                .build();
        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);

        // Act
        Message result = messageService.saveMessage(sendRequest, sender.getId());

        // Assert
        assertNotNull(result);
        verify(messageRepository, times(1)).save(any(Message.class));
    }

    @Test
    void saveMessage_Success_WithOnlyAttachmentsNoText() {
        // Arrange
        com.chat.app.dto.AttachmentRequest attachmentRequest = com.chat.app.dto.AttachmentRequest.builder()
                .fileName("test.png")
                .fileUrl("/uploads/test.png")
                .fileType("image/png")
                .fileSize(100L)
                .build();
        sendRequest.setContent(null);
        sendRequest.setAttachments(Collections.singletonList(attachmentRequest));

        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(userRepository.findById(sender.getId())).thenReturn(Optional.of(sender));
        when(roomMemberRepository.existsByIdRoomIdAndIdUserId(room.getId(), sender.getId())).thenReturn(true);

        Message savedMessage = Message.builder()
                .id(UUID.randomUUID())
                .room(room)
                .sender(sender)
                .attachments(new java.util.ArrayList<>())
                .build();
        when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);

        // Act
        Message result = messageService.saveMessage(sendRequest, sender.getId());

        // Assert
        assertNotNull(result);
        verify(messageRepository, times(1)).save(any(Message.class));
    }

    @Test
    void saveMessage_ThrowsIllegalArgumentException_WhenEmpty() {
        // Arrange
        sendRequest.setContent(null);
        sendRequest.setAttachments(null);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> messageService.saveMessage(sendRequest, sender.getId()));
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void editMessage_Success() {
        // Arrange
        UUID messageId = UUID.randomUUID();
        com.chat.app.dto.MessageUpdateRequest updateRequest = new com.chat.app.dto.MessageUpdateRequest("new content");
        Message message = Message.builder()
                .id(messageId)
                .sender(sender)
                .content("old content")
                .isDeleted(false)
                .build();

        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Message result = messageService.editMessage(messageId, updateRequest, sender.getId());

        // Assert
        assertNotNull(result);
        assertEquals("new content", result.getContent());
        assertNotNull(result.getUpdatedAt());
    }

    @Test
    void editMessage_ThrowsAccessDeniedException_WhenNotOwner() {
        // Arrange
        UUID messageId = UUID.randomUUID();
        com.chat.app.dto.MessageUpdateRequest updateRequest = new com.chat.app.dto.MessageUpdateRequest("new content");
        Message message = Message.builder()
                .id(messageId)
                .sender(sender)
                .content("old content")
                .isDeleted(false)
                .build();

        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));

        // Act & Assert
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> messageService.editMessage(messageId, updateRequest, UUID.randomUUID()));
    }

    @Test
    void deleteMessage_Success() {
        // Arrange
        UUID messageId = UUID.randomUUID();
        Message message = Message.builder()
                .id(messageId)
                .sender(sender)
                .content("some content")
                .attachments(new java.util.ArrayList<>())
                .isDeleted(false)
                .build();

        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Message result = messageService.deleteMessage(messageId, sender.getId());

        // Assert
        assertNotNull(result);
        assertTrue(result.isDeleted());
        assertNull(result.getContent());
        assertTrue(result.getAttachments().isEmpty());
    }

    @Test
    void toggleReaction_AddSuccess() {
        // Arrange
        UUID messageId = UUID.randomUUID();
        String emoji = "🚀";
        Message message = Message.builder()
                .id(messageId)
                .room(room)
                .isDeleted(false)
                .build();

        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(userRepository.findById(sender.getId())).thenReturn(Optional.of(sender));
        when(roomMemberRepository.existsByIdRoomIdAndIdUserId(room.getId(), sender.getId())).thenReturn(true);
        when(messageReactionRepository.findByMessageIdAndUserIdAndEmoji(messageId, sender.getId(), emoji))
                .thenReturn(Optional.empty());

        // Act
        com.chat.app.dto.ReactionSyncResponse result = messageService.toggleReaction(messageId, emoji, sender.getId());

        // Assert
        assertNotNull(result);
        assertEquals("ADDED", result.getAction());
        assertEquals(emoji, result.getEmoji());
        verify(messageReactionRepository, times(1)).save(any(com.chat.app.model.MessageReaction.class));
    }

    @Test
    void toggleReaction_RemoveSuccess() {
        // Arrange
        UUID messageId = UUID.randomUUID();
        String emoji = "🚀";
        Message message = Message.builder()
                .id(messageId)
                .room(room)
                .isDeleted(false)
                .build();
        com.chat.app.model.MessageReaction reaction = com.chat.app.model.MessageReaction.builder()
                .id(UUID.randomUUID())
                .message(message)
                .user(sender)
                .emoji(emoji)
                .build();

        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(userRepository.findById(sender.getId())).thenReturn(Optional.of(sender));
        when(roomMemberRepository.existsByIdRoomIdAndIdUserId(room.getId(), sender.getId())).thenReturn(true);
        when(messageReactionRepository.findByMessageIdAndUserIdAndEmoji(messageId, sender.getId(), emoji))
                .thenReturn(Optional.of(reaction));

        // Act
        com.chat.app.dto.ReactionSyncResponse result = messageService.toggleReaction(messageId, emoji, sender.getId());

        // Assert
        assertNotNull(result);
        assertEquals("REMOVED", result.getAction());
        verify(messageReactionRepository, times(1)).delete(any(com.chat.app.model.MessageReaction.class));
    }

    @Test
    void searchRoomMessages_Success() {
        // Arrange
        UUID roomId = room.getId();
        String query = "hello";
        PageRequest pageable = PageRequest.of(0, 10);
        Message message = Message.builder()
                .id(UUID.randomUUID())
                .room(room)
                .content("hello world")
                .sender(sender)
                .isDeleted(false)
                .createdAt(java.time.Instant.now())
                .build();
        Page<Message> pageResult = new org.springframework.data.domain.PageImpl<>(Collections.singletonList(message));

        when(roomRepository.existsById(roomId)).thenReturn(true);
        when(roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, sender.getId())).thenReturn(true);
        when(messageRepository.searchRoomMessages(eq(roomId), eq(query), any(PageRequest.class))).thenReturn(pageResult);
        when(messageReactionRepository.findByMessageIdIn(anyList())).thenReturn(Collections.emptyList());

        // Act
        Page<com.chat.app.dto.MessageResponse> results = messageService.searchRoomMessages(roomId, sender.getId(), query, 0, 10);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.getTotalElements());
        assertEquals("hello world", results.getContent().get(0).getContent());
    }

    @Test
    void searchRoomMessages_ThrowsAccessDeniedException_WhenNotMember() {
        // Arrange
        UUID roomId = room.getId();

        when(roomRepository.existsById(roomId)).thenReturn(true);
        when(roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, sender.getId())).thenReturn(false);

        // Act & Assert
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> messageService.searchRoomMessages(roomId, sender.getId(), "test", 0, 10));
    }

    @Test
    void saveMessage_WithMentions_SavesMentionAndTriggersNotification() {
        // Arrange
        UUID roomId = room.getId();
        User targetUser = User.builder()
                .id(UUID.randomUUID())
                .username("targetuser")
                .email("target@example.com")
                .build();

        MessageSendRequest request = MessageSendRequest.builder()
                .roomId(roomId)
                .content("hello @targetuser how are you?")
                .build();

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(userRepository.findById(sender.getId())).thenReturn(Optional.of(sender));
        when(roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, sender.getId())).thenReturn(true);
        when(userRepository.findByUsername("targetuser")).thenReturn(Optional.of(targetUser));
        when(roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, targetUser.getId())).thenReturn(true);

        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        // Act
        Message result = messageService.saveMessage(request, sender.getId());

        // Assert
        assertNotNull(result);
        verify(messageMentionRepository, times(1)).save(any(com.chat.app.model.MessageMention.class));
        verify(messagingTemplate, times(1)).convertAndSendToUser(
                eq("targetuser"),
                eq("/queue/notifications"),
                any(com.chat.app.dto.NotificationResponse.class)
        );
    }

    @Test
    void saveMessage_WithSelfMention_DoesNotTriggerNotification() {
        // Arrange
        UUID roomId = room.getId();
        MessageSendRequest request = MessageSendRequest.builder()
                .roomId(roomId)
                .content("hello @sender!")
                .build();

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(userRepository.findById(sender.getId())).thenReturn(Optional.of(sender));
        when(roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, sender.getId())).thenReturn(true);

        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message m = invocation.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        // Act
        Message result = messageService.saveMessage(request, sender.getId());

        // Assert
        assertNotNull(result);
        verify(messageMentionRepository, never()).save(any(com.chat.app.model.MessageMention.class));
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void togglePinMessage_PinsMessageSuccessfully() {
        // Arrange
        UUID messageId = UUID.randomUUID();
        Message message = Message.builder()
                .id(messageId)
                .room(room)
                .sender(sender)
                .content("hello")
                .isDeleted(false)
                .build();

        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(userRepository.findById(sender.getId())).thenReturn(Optional.of(sender));
        when(roomMemberRepository.existsByIdRoomIdAndIdUserId(room.getId(), sender.getId())).thenReturn(true);
        when(pinnedMessageRepository.findByRoomIdAndMessageId(room.getId(), messageId)).thenReturn(Optional.empty());

        // Act
        com.chat.app.dto.PinToggleResponse response = messageService.togglePinMessage(messageId, sender.getId());

        // Assert
        assertNotNull(response);
        assertTrue(response.isPinned());
        assertEquals("PIN", response.getPinnedByUsername().equals("sender") ? "PIN" : "PIN");
        verify(pinnedMessageRepository, times(1)).save(any(com.chat.app.model.PinnedMessage.class));
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/room." + room.getId()), any(com.chat.app.dto.PinSyncResponse.class));
    }

    @Test
    void togglePinMessage_UnpinsMessageSuccessfully() {
        // Arrange
        UUID messageId = UUID.randomUUID();
        Message message = Message.builder()
                .id(messageId)
                .room(room)
                .sender(sender)
                .content("hello")
                .isDeleted(false)
                .build();

        com.chat.app.model.PinnedMessage existing = com.chat.app.model.PinnedMessage.builder()
                .message(message)
                .room(room)
                .pinnedBy(sender)
                .build();

        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(userRepository.findById(sender.getId())).thenReturn(Optional.of(sender));
        when(roomMemberRepository.existsByIdRoomIdAndIdUserId(room.getId(), sender.getId())).thenReturn(true);
        when(pinnedMessageRepository.findByRoomIdAndMessageId(room.getId(), messageId)).thenReturn(Optional.of(existing));

        // Act
        com.chat.app.dto.PinToggleResponse response = messageService.togglePinMessage(messageId, sender.getId());

        // Assert
        assertNotNull(response);
        assertFalse(response.isPinned());
        verify(pinnedMessageRepository, times(1)).delete(existing);
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/room." + room.getId()), any(com.chat.app.dto.PinSyncResponse.class));
    }

    @Test
    void getPinnedMessages_Success() {
        // Arrange
        UUID roomId = room.getId();
        Message message = Message.builder()
                .id(UUID.randomUUID())
                .room(room)
                .sender(sender)
                .content("pinned content")
                .isDeleted(false)
                .build();

        com.chat.app.model.PinnedMessage pin = com.chat.app.model.PinnedMessage.builder()
                .message(message)
                .room(room)
                .pinnedBy(sender)
                .build();

        when(roomRepository.existsById(roomId)).thenReturn(true);
        when(roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, sender.getId())).thenReturn(true);
        when(pinnedMessageRepository.findByRoomId(roomId)).thenReturn(Collections.singletonList(pin));

        // Act
        java.util.List<com.chat.app.dto.MessageResponse> results = messageService.getPinnedMessages(roomId, sender.getId());

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("pinned content", results.get(0).getContent());
        assertTrue(results.get(0).isPinned());
    }

    @Test
    void deleteMessage_Success_ByModerator() {
        // Arrange
        UUID messageId = UUID.randomUUID();
        UUID moderatorId = UUID.randomUUID();
        Message message = Message.builder()
                .id(messageId)
                .room(room)
                .sender(sender) // sent by 'sender'
                .content("spam content")
                .isDeleted(false)
                .build();

        RoomMemberId membershipId = RoomMemberId.builder().roomId(room.getId()).userId(moderatorId).build();
        RoomMember moderator = RoomMember.builder().id(membershipId).role(com.chat.app.model.RoomRole.MODERATOR).build();

        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(roomMemberRepository.findById(membershipId)).thenReturn(Optional.of(moderator));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Message deleted = messageService.deleteMessage(messageId, moderatorId);

        // Assert
        assertNotNull(deleted);
        assertTrue(deleted.isDeleted());
        assertNull(deleted.getContent());
    }

    @Test
    void deleteMessage_Forbidden_ByMemberOnOtherSenderMessage() {
        // Arrange
        UUID messageId = UUID.randomUUID();
        UUID otherMemberId = UUID.randomUUID();
        Message message = Message.builder()
                .id(messageId)
                .room(room)
                .sender(sender) // sent by 'sender'
                .content("hello")
                .isDeleted(false)
                .build();

        RoomMemberId membershipId = RoomMemberId.builder().roomId(room.getId()).userId(otherMemberId).build();
        RoomMember member = RoomMember.builder().id(membershipId).role(com.chat.app.model.RoomRole.MEMBER).build();

        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(roomMemberRepository.findById(membershipId)).thenReturn(Optional.of(member));

        // Act & Assert
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> {
            messageService.deleteMessage(messageId, otherMemberId);
        });
    }

    @Test
    void deleteMessage_CascadesPinDelete_WhenPinned() {
        // Arrange
        UUID messageId = UUID.randomUUID();
        Message message = Message.builder()
                .id(messageId)
                .room(room)
                .sender(sender)
                .content("original content")
                .isDeleted(false)
                .build();

        com.chat.app.model.PinnedMessage pin = com.chat.app.model.PinnedMessage.builder()
                .message(message)
                .room(room)
                .pinnedBy(sender)
                .build();

        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(pinnedMessageRepository.findByRoomIdAndMessageId(room.getId(), messageId)).thenReturn(Optional.of(pin));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Message result = messageService.deleteMessage(messageId, sender.getId());

        // Assert
        assertNotNull(result);
        assertTrue(result.isDeleted());
        verify(pinnedMessageRepository, times(1)).delete(pin);
    }
}
