package com.chat.app.config;

import com.chat.app.dto.MessageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisPubSubTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ChannelTopic topic;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private RedisPublisher redisPublisher;
    private RedisSubscriber redisSubscriber;

    @BeforeEach
    void setUp() {
        redisPublisher = new RedisPublisher(redisTemplate, topic, messagingTemplate);
        redisSubscriber = new RedisSubscriber(messagingTemplate);
    }

    @Test
    void publish_Success() {
        // Arrange
        MessageResponse payload = MessageResponse.builder()
                .id(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .content("hello redis")
                .build();
        when(topic.getTopic()).thenReturn("chat-events");

        // Act
        redisPublisher.publish(payload);

        // Assert
        verify(redisTemplate, times(1)).convertAndSend(eq("chat-events"), eq(payload));
    }

    @Test
    void publish_Fallback_OnFailure() {
        // Arrange
        MessageResponse payload = MessageResponse.builder()
                .id(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .content("hello fallback")
                .build();
        when(topic.getTopic()).thenReturn("chat-events");
        doThrow(new RuntimeException("Redis connection error")).when(redisTemplate)
                .convertAndSend(any(String.class), any(Object.class));

        // Act
        redisPublisher.publish(payload);

        // Assert
        verify(messagingTemplate, times(1)).convertAndSend(
                eq("/topic/room." + payload.getRoomId()),
                eq(payload)
        );
    }

    @Test
    void subscriber_RelaysMessage() throws IOException {
        // Arrange
        MessageResponse payload = MessageResponse.builder()
                .id(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .content("relayed message")
                .senderUsername("testuser")
                .timestamp(Instant.now())
                .build();

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        byte[] payloadBytes = mapper.writeValueAsBytes(payload);

        Message mockMessage = mock(Message.class);
        when(mockMessage.getBody()).thenReturn(payloadBytes);

        // Act
        redisSubscriber.onMessage(mockMessage, new byte[0]);

        // Assert
        verify(messagingTemplate, times(1)).convertAndSend(
                eq("/topic/room." + payload.getRoomId()),
                any(MessageResponse.class)
        );
    }
}
