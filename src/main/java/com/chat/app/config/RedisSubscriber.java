package com.chat.app.config;

import com.chat.app.dto.MessageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class RedisSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public RedisSubscriber(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            byte[] body = message.getBody();
            MessageResponse response = objectMapper.readValue(body, MessageResponse.class);
            
            String destination = "/topic/room." + response.getRoomId();
            messagingTemplate.convertAndSend(destination, response);
            
            log.info("Successfully relayed message {} to WebSocket destination {}", response.getId(), destination);
        } catch (IOException e) {
            log.error("Failed to deserialize Redis message payload", e);
        }
    }
}
