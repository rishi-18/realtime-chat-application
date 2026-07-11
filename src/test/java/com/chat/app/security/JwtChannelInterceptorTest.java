package com.chat.app.security;

import com.chat.app.repository.RoomMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtChannelInterceptorTest {

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private CustomUserDetailsService customUserDetailsService;

    @Mock
    private RoomMemberRepository roomMemberRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private MessageChannel messageChannel;

    @Mock
    private WebSocketSessionBlacklist sessionBlacklist;

    @InjectMocks
    private JwtChannelInterceptor jwtChannelInterceptor;

    private UserPrincipal userPrincipal;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        userPrincipal = new UserPrincipal(UUID.randomUUID(), "testuser", "test@test.com", "password", Collections.emptyList());
        userDetails = userPrincipal;
    }

    @Test
    void preSend_ConnectFrame_Success() {
        // Arrange
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer testtoken");
        accessor.setSessionAttributes(new HashMap<>());
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(tokenProvider.validateToken("testtoken")).thenReturn(true);
        when(tokenProvider.getUserIdFromJWT("testtoken")).thenReturn(userPrincipal.getId());
        when(customUserDetailsService.loadUserById(userPrincipal.getId())).thenReturn(userDetails);
        when(tokenProvider.getTokenExpiryFromJWT("testtoken")).thenReturn(new Date(System.currentTimeMillis() + 100000));

        // Act
        Message<?> result = jwtChannelInterceptor.preSend(message, messageChannel);

        // Assert
        assertNotNull(result);
        StompHeaderAccessor resAccessor = StompHeaderAccessor.wrap(result);
        assertNotNull(resAccessor.getUser());
        assertEquals(userPrincipal.getUsername(), resAccessor.getUser().getName());
        assertNotNull(resAccessor.getSessionAttributes().get("token_expiry"));
    }

    @Test
    void preSend_ExpiredSession_RejectsFrame() {
        // Arrange
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/room." + UUID.randomUUID());
        HashMap<String, Object> attrs = new HashMap<>();
        // Set expiry in the past
        attrs.put("token_expiry", System.currentTimeMillis() - 5000);
        accessor.setSessionAttributes(attrs);
        accessor.setUser(new UsernamePasswordAuthenticationToken(userPrincipal, null));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        // Act & Assert
        assertThrows(AccessDeniedException.class, () -> jwtChannelInterceptor.preSend(message, messageChannel));
    }

    @Test
    void preSend_BlacklistedSession_RejectsFrame() {
        // Arrange
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/room." + UUID.randomUUID());
        HashMap<String, Object> attrs = new HashMap<>();
        // Set future expiry
        attrs.put("token_expiry", System.currentTimeMillis() + 100000);
        accessor.setSessionAttributes(attrs);
        accessor.setUser(new UsernamePasswordAuthenticationToken(userPrincipal, null));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(sessionBlacklist.isUserBlacklisted(userPrincipal.getId())).thenReturn(true);

        // Act & Assert
        assertThrows(AccessDeniedException.class, () -> jwtChannelInterceptor.preSend(message, messageChannel));
    }
}
