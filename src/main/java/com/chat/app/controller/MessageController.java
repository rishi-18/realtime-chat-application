package com.chat.app.controller;

import com.chat.app.dto.MessageResponse;
import com.chat.app.dto.MessageSendRequest;
import com.chat.app.model.Message;
import com.chat.app.security.UserPrincipal;
import com.chat.app.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@Slf4j
public class MessageController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final com.chat.app.service.RoomService roomService;
    private final com.chat.app.config.RedisPublisher redisPublisher;

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload @Valid MessageSendRequest request, Principal principal) {
        if (principal == null) {
            log.warn("Anonymous message transmission attempt rejected.");
            return;
        }

        UserPrincipal userPrincipal = (UserPrincipal) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
        log.debug("Received real-time message from user: {} for room: {}", userPrincipal.getUsername(), request.getRoomId());

        // Persistence in PostgreSQL (synchronous validation)
        Message message = messageService.saveMessage(request, userPrincipal.getId());

        // Assemble broadcast payload
        MessageResponse response = messageService.mapToResponse(message);

        // Publish to shared Redis Pub/Sub channel
        redisPublisher.publish(response);
        log.debug("Published message {} to Redis Pub/Sub", message.getId());
    }

    @MessageMapping("/chat.typing")
    public void typingState(@Payload @Valid com.chat.app.dto.TypingRequest request, Principal principal) {
        if (principal == null) {
            log.warn("Anonymous typing indicator transmission attempt rejected.");
            return;
        }

        UserPrincipal userPrincipal = (UserPrincipal) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
        log.debug("Received typing state from user: {} for room: {}", userPrincipal.getUsername(), request.getRoomId());

        if (!roomService.isUserMemberOfRoom(request.getRoomId(), userPrincipal.getId())) {
            log.warn("User {} attempted unauthorized typing alert inside room {}", userPrincipal.getUsername(), request.getRoomId());
            return;
        }

        // Assemble transient broadcast payload
        com.chat.app.dto.TypingResponse response = com.chat.app.dto.TypingResponse.builder()
                .roomId(request.getRoomId())
                .userId(userPrincipal.getId())
                .username(userPrincipal.getUsername())
                .typing(request.getTyping())
                .build();

        // Broadcast to transient typing sub-topic destination
        String destination = "/topic/typing." + request.getRoomId();
        messagingTemplate.convertAndSend(destination, response);
        log.debug("Broadcasted typing state to destination {}", destination);
    }

    @MessageMapping("/chat.readReceipt")
    public void readReceipt(@Payload @Valid com.chat.app.dto.ReadReceiptRequest request, Principal principal) {
        if (principal == null) {
            log.warn("Anonymous read receipt transmission attempt rejected.");
            return;
        }

        UserPrincipal userPrincipal = (UserPrincipal) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
        log.debug("Received read receipt from user: {} for message: {}", userPrincipal.getUsername(), request.getMessageId());

        // Update read pointer in database
        roomService.updateLastReadMessage(userPrincipal.getId(), request.getRoomId(), request.getMessageId());

        // Assemble broadcast payload
        com.chat.app.dto.ReadReceiptResponse response = com.chat.app.dto.ReadReceiptResponse.builder()
                .roomId(request.getRoomId())
                .userId(userPrincipal.getId())
                .messageId(request.getMessageId())
                .timestamp(java.time.Instant.now())
                .build();

        // Broadcast to sub-topic receipts destination
        String destination = "/topic/receipts." + request.getRoomId();
        messagingTemplate.convertAndSend(destination, response);
        log.debug("Broadcasted read receipt to destination {}", destination);
    }
}
