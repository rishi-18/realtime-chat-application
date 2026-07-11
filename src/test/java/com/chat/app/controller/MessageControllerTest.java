package com.chat.app.controller;

import com.chat.app.dto.MessageResponse;
import com.chat.app.dto.MessageSendRequest;
import com.chat.app.dto.TypingRequest;
import com.chat.app.dto.TypingResponse;
import com.chat.app.dto.ReadReceiptRequest;
import com.chat.app.dto.ReadReceiptResponse;
import com.chat.app.model.Message;
import com.chat.app.model.Room;
import com.chat.app.security.UserPrincipal;
import com.chat.app.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageControllerTest {

    @Mock
    private MessageService messageService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private com.chat.app.service.RoomService roomService;

    @InjectMocks
    private MessageController messageController;

    private Principal mockPrincipal;
    private UserPrincipal userPrincipal;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        userPrincipal = new UserPrincipal(userId, "testuser", "test@test.com", "password", java.util.Collections.emptyList());
        mockPrincipal = new UsernamePasswordAuthenticationToken(userPrincipal, null);
    }

    @Test
    void sendMessage_Success() {
        // Arrange
        UUID roomId = UUID.randomUUID();
        MessageSendRequest request = MessageSendRequest.builder()
                .roomId(roomId)
                .content("hello world")
                .build();

        Room room = Room.builder().id(roomId).name("testroom").build();
        Message message = Message.builder()
                .id(UUID.randomUUID())
                .room(room)
                .sender(com.chat.app.model.User.builder().id(userId).username("testuser").build())
                .content("hello world")
                .createdAt(Instant.now())
                .build();

        when(messageService.saveMessage(any(MessageSendRequest.class), eq(userId))).thenReturn(message);
        when(messageService.mapToResponse(any(Message.class))).thenCallRealMethod();
        when(messageService.mapToResponse(any(Message.class), any())).thenCallRealMethod();

        // Act
        messageController.sendMessage(request, mockPrincipal);

        // Assert
        verify(messageService, times(1)).saveMessage(any(MessageSendRequest.class), eq(userId));

        ArgumentCaptor<MessageResponse> captor = ArgumentCaptor.forClass(MessageResponse.class);
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/room." + roomId), captor.capture());

        MessageResponse response = captor.getValue();
        assertEquals("hello world", response.getContent());
        assertEquals(roomId, response.getRoomId());
        assertEquals(userId, response.getSenderId());
    }

    @Test
    void typingState_Success() {
        // Arrange
        UUID roomId = UUID.randomUUID();
        TypingRequest request = TypingRequest.builder()
                .roomId(roomId)
                .typing(true)
                .build();

        when(roomService.isUserMemberOfRoom(eq(roomId), eq(userId))).thenReturn(true);

        // Act
        messageController.typingState(request, mockPrincipal);

        // Assert
        ArgumentCaptor<TypingResponse> captor = ArgumentCaptor.forClass(TypingResponse.class);
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/typing." + roomId), captor.capture());

        TypingResponse response = captor.getValue();
        assertEquals(roomId, response.getRoomId());
        assertEquals(userId, response.getUserId());
        assertEquals("testuser", response.getUsername());
        assertTrue(response.getTyping());
    }

    @Test
    void sendMessage_AnonymousAttempt_Ignored() {
        // Act
        messageController.sendMessage(new MessageSendRequest(), null);

        // Assert
        verifyNoInteractions(messageService);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void typingState_AnonymousAttempt_Ignored() {
        // Act
        messageController.typingState(new TypingRequest(), null);

        // Assert
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void readReceipt_Success() {
        // Arrange
        UUID roomId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        ReadReceiptRequest request = ReadReceiptRequest.builder()
                .roomId(roomId)
                .messageId(messageId)
                .build();

        // Act
        messageController.readReceipt(request, mockPrincipal);

        // Assert
        verify(roomService, times(1)).updateLastReadMessage(eq(userId), eq(roomId), eq(messageId));

        ArgumentCaptor<ReadReceiptResponse> captor = ArgumentCaptor.forClass(ReadReceiptResponse.class);
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/receipts." + roomId), captor.capture());

        ReadReceiptResponse response = captor.getValue();
        assertEquals(roomId, response.getRoomId());
        assertEquals(userId, response.getUserId());
        assertEquals(messageId, response.getMessageId());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void readReceipt_AnonymousAttempt_Ignored() {
        // Act
        messageController.readReceipt(new ReadReceiptRequest(), null);

        // Assert
        verifyNoInteractions(roomService);
        verifyNoInteractions(messagingTemplate);
    }
}
