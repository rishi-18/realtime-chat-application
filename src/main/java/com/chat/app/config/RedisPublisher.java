package com.chat.app.config;

import com.chat.app.dto.MessageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RedisPublisher {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ChannelTopic topic;
    private final SimpMessagingTemplate messagingTemplate;

    public RedisPublisher(
            RedisTemplate<String, Object> redisTemplate,
            ChannelTopic topic,
            SimpMessagingTemplate messagingTemplate) {
        this.redisTemplate = redisTemplate;
        this.topic = topic;
        this.messagingTemplate = messagingTemplate;
    }

    public void publish(MessageResponse message) {
        try {
            redisTemplate.convertAndSend(topic.getTopic(), message);
            log.debug("Successfully published message {} to Redis Pub/Sub topic", message.getId());
        } catch (Exception ex) {
            log.warn("Failed to publish message {} to Redis. Falling back to local WebSocket broadcast. Error: {}", message.getId(), ex.getMessage());
            // Safe fallback to direct WebSocket relay for active instance
            String destination = "/topic/room." + message.getRoomId();
            messagingTemplate.convertAndSend(destination, message);
        }
    }
}
