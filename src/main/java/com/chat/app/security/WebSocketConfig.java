package com.chat.app.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtChannelInterceptor jwtChannelInterceptor;
    private final com.chat.app.config.RateLimitingInterceptor rateLimitingInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register connection endpoint, mapping CORS mappings to permit development clients
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Broadcast topics prefix destination
        registry.enableSimpleBroker("/topic");
        // Outgoing message mappings destination prefix
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(org.springframework.messaging.simp.config.ChannelRegistration registration) {
        // Plug in custom authentication/authorization interceptor for processing input frames
        registration.interceptors(jwtChannelInterceptor, rateLimitingInterceptor);
    }
}
