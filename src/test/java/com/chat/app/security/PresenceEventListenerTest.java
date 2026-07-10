package com.chat.app.security;

import com.chat.app.dto.PresenceEvent;
import com.chat.app.model.Room;
import com.chat.app.model.RoomMember;
import com.chat.app.model.User;
import com.chat.app.repository.RoomMemberRepository;
import com.chat.app.repository.UserRepository;
import com.chat.app.service.PresenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PresenceEventListenerTest {

    @Mock
    private PresenceService presenceService;

    @Mock
    private RoomMemberRepository roomMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private PresenceEventListener presenceEventListener;

    private UserPrincipal userPrincipal;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        userPrincipal = new UserPrincipal(userId, "testuser", "testuser@example.com", "password", Collections.emptyList());
    }

    @Test
    void handleWebSocketConnectListener_BroadcastsONLINE_WhenUserOffline() {
        // Arrange
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-1");
        accessor.setUser(new UsernamePasswordAuthenticationToken(userPrincipal, null));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        SessionConnectEvent event = new SessionConnectEvent(this, message, accessor.getUser());

        when(presenceService.isUserOnline(userId)).thenReturn(false);

        Room room = Room.builder().id(UUID.randomUUID()).name("Room 1").build();
        RoomMember membership = RoomMember.builder().room(room).build();
        when(roomMemberRepository.findByIdUserId(userId)).thenReturn(Collections.singletonList(membership));

        // Act
        presenceEventListener.handleWebSocketConnectListener(event);

        // Assert
        verify(presenceService, times(1)).markOnline(userId, "session-1");
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/presence." + room.getId()), any(PresenceEvent.class));
    }

    @Test
    void handleWebSocketConnectListener_DoesNotBroadcastONLINE_WhenUserAlreadyOnline() {
        // Arrange
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("session-2");
        accessor.setUser(new UsernamePasswordAuthenticationToken(userPrincipal, null));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        SessionConnectEvent event = new SessionConnectEvent(this, message, accessor.getUser());

        when(presenceService.isUserOnline(userId)).thenReturn(true);

        // Act
        presenceEventListener.handleWebSocketConnectListener(event);

        // Assert
        verify(presenceService, times(1)).markOnline(userId, "session-2");
        verify(roomMemberRepository, never()).findByIdUserId(any(UUID.class));
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(PresenceEvent.class));
    }

    @Test
    void handleWebSocketDisconnectListener_BroadcastsOFFLINE_WhenUserTransitionsOffline() {
        // Arrange
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId("session-1");
        accessor.setUser(new UsernamePasswordAuthenticationToken(userPrincipal, null));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        SessionDisconnectEvent event = new SessionDisconnectEvent(this, message, "session-1", null);

        when(presenceService.markOffline("session-1")).thenReturn(userId);

        User user = User.builder().id(userId).username("testuser").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Room room = Room.builder().id(UUID.randomUUID()).name("Room 1").build();
        RoomMember membership = RoomMember.builder().room(room).build();
        when(roomMemberRepository.findByIdUserId(userId)).thenReturn(Collections.singletonList(membership));

        // Act
        presenceEventListener.handleWebSocketDisconnectListener(event);

        // Assert
        verify(presenceService, times(1)).markOffline("session-1");
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/presence." + room.getId()), any(PresenceEvent.class));
    }
}
