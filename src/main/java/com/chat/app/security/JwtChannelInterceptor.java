package com.chat.app.security;

import com.chat.app.repository.RoomMemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.UUID;

@Component
@Slf4j
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final RoomMemberRepository roomMemberRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public JwtChannelInterceptor(
            JwtTokenProvider tokenProvider,
            CustomUserDetailsService customUserDetailsService,
            RoomMemberRepository roomMemberRepository,
            @Lazy SimpMessagingTemplate messagingTemplate) {
        this.tokenProvider = tokenProvider;
        this.customUserDetailsService = customUserDetailsService;
        this.roomMemberRepository = roomMemberRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null) {
            StompCommand command = accessor.getCommand();

            if (StompCommand.CONNECT.equals(command)) {
                String bearerToken = accessor.getFirstNativeHeader("Authorization");
                log.debug("Intercepted CONNECT frame. Auth Header: {}", bearerToken);

                if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
                    String token = bearerToken.substring(7);
                    if (tokenProvider.validateToken(token)) {
                        UUID userId = tokenProvider.getUserIdFromJWT(token);
                        UserDetails userDetails = customUserDetailsService.loadUserById(userId);

                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities()
                        );
                        // Save the user details inside the STOMP session headers
                        accessor.setUser(authentication);
                        log.debug("Successfully authenticated STOMP connection for user: {}", userDetails.getUsername());
                    } else {
                        log.warn("Invalid JWT signature supplied in STOMP CONNECT frame.");
                    }
                } else {
                    log.warn("Missing or malformed Authorization header in STOMP CONNECT frame.");
                }
            } else if (StompCommand.SUBSCRIBE.equals(command)) {
                Principal principal = accessor.getUser();
                if (principal == null) {
                    throw new AccessDeniedException("Unauthorized subscription attempt. Session principal is missing.");
                }

                String destination = accessor.getDestination();
                log.debug("Intercepted SUBSCRIBE frame. Destination: {}, Principal: {}", destination, principal.getName());

                String roomIdStr = null;
                if (StringUtils.hasText(destination)) {
                    if (destination.startsWith("/topic/room.")) {
                        roomIdStr = destination.substring("/topic/room.".length());
                    } else if (destination.startsWith("/topic/typing.")) {
                        roomIdStr = destination.substring("/topic/typing.".length());
                    } else if (destination.startsWith("/topic/receipts.")) {
                        roomIdStr = destination.substring("/topic/receipts.".length());
                    } else if (destination.startsWith("/topic/presence.")) {
                        roomIdStr = destination.substring("/topic/presence.".length());
                    }
                }

                if (roomIdStr != null) {
                    try {
                        UUID roomId = UUID.fromString(roomIdStr);
                        UserPrincipal userPrincipal = (UserPrincipal) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();

                        // Enforce private channel authorization: check if user is a joined member
                        boolean isMember = roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userPrincipal.getId());
                        if (!isMember) {
                            log.warn("User {} attempted unauthorized subscription to topic: {}", userPrincipal.getUsername(), destination);
                            
                            // Send error payload to user's private queue instead of throwing exception
                            // This keeps the underlying TCP/WebSocket connection open
                            messagingTemplate.convertAndSendToUser(
                                    userPrincipal.getUsername(),
                                    "/queue/errors",
                                    new com.chat.app.dto.ApiResponse(false, "Access denied. You are not a member of this chat room.")
                            );
                            
                            // Discard this SUBSCRIBE frame
                            return null;
                        }
                    } catch (IllegalArgumentException ex) {
                        log.warn("Malformed UUID specified in subscribe topic: {}", destination);
                        
                        // Send error to user's private queue and discard the frame
                        UserPrincipal userPrincipal = (UserPrincipal) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
                        messagingTemplate.convertAndSendToUser(
                                userPrincipal.getUsername(),
                                "/queue/errors",
                                new com.chat.app.dto.ApiResponse(false, "Invalid room destination payload.")
                        );
                        return null;
                    }
                }
            }
        }

        return message;
    }
}
