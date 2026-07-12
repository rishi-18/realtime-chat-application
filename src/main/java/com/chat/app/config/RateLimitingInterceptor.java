package com.chat.app.config;

import com.chat.app.dto.ApiResponse;
import com.chat.app.security.UserPrincipal;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
@Slf4j
public class RateLimitingInterceptor implements ChannelInterceptor {

    private final RateLimitConfig rateLimitConfig;
    private final SimpMessagingTemplate messagingTemplate;

    public RateLimitingInterceptor(RateLimitConfig rateLimitConfig, @Lazy SimpMessagingTemplate messagingTemplate) {
        this.rateLimitConfig = rateLimitConfig;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        SimpMessageHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, SimpMessageHeaderAccessor.class);
        if (accessor != null && SimpMessageType.MESSAGE.equals(accessor.getMessageType())) {
            Principal principal = accessor.getUser();
            if (principal instanceof Authentication) {
                Authentication auth = (Authentication) principal;
                if (auth.getPrincipal() instanceof UserPrincipal) {
                    UserPrincipal userPrincipal = (UserPrincipal) auth.getPrincipal();
                    String key = userPrincipal.getId().toString();
                    Bucket bucket = rateLimitConfig.resolveWsBucket(key);

                    if (!bucket.tryConsume(1)) {
                        log.warn("WebSocket rate limit hit for user: {}", userPrincipal.getUsername());
                        
                        // Send error to user queue
                        String sessionId = accessor.getSessionId();
                        if (sessionId != null) {
                            ApiResponse errorResponse = new ApiResponse(false, "WebSocket message limit exceeded. Stop spamming.");
                            messagingTemplate.convertAndSendToUser(
                                    userPrincipal.getUsername(),
                                    "/queue/errors",
                                    errorResponse,
                                    accessor.getMessageHeaders()
                            );
                        }
                        
                        // Returning null drops the message from processing queue
                        return null;
                    }
                }
            }
        }
        return message;
    }
}
