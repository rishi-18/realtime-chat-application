package com.chat.app.config;

import com.chat.app.dto.ApiResponse;
import com.chat.app.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitingInterceptorTest {

    private RateLimitingInterceptor rateLimitingInterceptor;

    @Mock
    private RedisRateLimiter redisRateLimiter;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private MessageChannel messageChannel;

    private UserPrincipal userPrincipal;
    private Principal authPrincipal;

    @BeforeEach
    void setUp() {
        rateLimitingInterceptor = new RateLimitingInterceptor(redisRateLimiter, messagingTemplate);
        userPrincipal = new UserPrincipal(
                UUID.randomUUID(),
                "testuser",
                "test@example.com",
                "password",
                Collections.emptyList()
        );
        authPrincipal = new UsernamePasswordAuthenticationToken(userPrincipal, null);
    }

    @Test
    void preSend_AllowsMessage_WhenTokensAvailable() {
        // Arrange
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setUser(authPrincipal);
        accessor.setSessionId("session-1");
        Message<String> message = MessageBuilder.createMessage("hello", accessor.getMessageHeaders());

        String redisKey = "ratelimit:ws:" + userPrincipal.getId();
        when(redisRateLimiter.tryConsume(redisKey, 30, 60)).thenReturn(true);

        // Act
        Message<?> result = rateLimitingInterceptor.preSend(message, messageChannel);

        // Assert
        assertNotNull(result);
        assertEquals(message, result);
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(), anyMap());
    }

    @Test
    void preSend_DropsMessage_WhenRateLimitExceeded() {
        // Arrange
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setUser(authPrincipal);
        accessor.setSessionId("session-1");
        Message<String> message = MessageBuilder.createMessage("hello", accessor.getMessageHeaders());

        String redisKey = "ratelimit:ws:" + userPrincipal.getId();
        when(redisRateLimiter.tryConsume(redisKey, 30, 60)).thenReturn(false);

        // Act
        Message<?> result = rateLimitingInterceptor.preSend(message, messageChannel);

        // Assert
        assertNull(result); // Null returned indicates dropped message
        verify(messagingTemplate, times(1)).convertAndSendToUser(
                eq(userPrincipal.getUsername()),
                eq("/queue/errors"),
                any(ApiResponse.class),
                org.mockito.ArgumentMatchers.anyMap()
        );
    }
}
