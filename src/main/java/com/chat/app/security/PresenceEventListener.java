package com.chat.app.security;

import com.chat.app.dto.PresenceEvent;
import com.chat.app.model.RoomMember;
import com.chat.app.model.User;
import com.chat.app.repository.RoomMemberRepository;
import com.chat.app.repository.UserRepository;
import com.chat.app.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PresenceEventListener {

    private final PresenceService presenceService;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = headerAccessor.getUser();

        if (principal instanceof UsernamePasswordAuthenticationToken) {
            UserPrincipal userPrincipal = (UserPrincipal) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
            String sessionId = headerAccessor.getSessionId();

            log.info("WebSocket connection established. User: {}, Session: {}", userPrincipal.getUsername(), sessionId);

            boolean alreadyOnline = presenceService.isUserOnline(userPrincipal.getId());
            presenceService.markOnline(userPrincipal.getId(), sessionId);

            if (!alreadyOnline) {
                // First connection: transition to ONLINE and broadcast state change
                broadcastPresenceStatus(userPrincipal.getId(), userPrincipal.getUsername(), "ONLINE");
            }
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        // Mark offline and get userId if connection count reached 0
        UUID userId = presenceService.markOffline(sessionId);

        if (userId != null) {
            // Find user details to retrieve username for broadcast
            String username = userRepository.findById(userId)
                    .map(User::getUsername)
                    .orElse("Unknown User");

            log.info("WebSocket connection closed. User: {} [{}], Session: {}", username, userId, sessionId);
            broadcastPresenceStatus(userId, username, "OFFLINE");
        }
    }

    private void broadcastPresenceStatus(UUID userId, String username, String status) {
        // Fetch all channels the user belongs to
        List<RoomMember> memberships = roomMemberRepository.findByIdUserId(userId);
        log.debug("Broadcasting presence [{}] for user {} across {} channels", status, username, memberships.size());

        PresenceEvent presenceEvent = PresenceEvent.builder()
                .userId(userId)
                .username(username)
                .status(status)
                .timestamp(Instant.now())
                .build();

        for (RoomMember membership : memberships) {
            UUID roomId = membership.getRoom().getId();
            String destination = "/topic/presence." + roomId;
            messagingTemplate.convertAndSend(destination, presenceEvent);
        }
    }
}
