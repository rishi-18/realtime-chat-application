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
}
