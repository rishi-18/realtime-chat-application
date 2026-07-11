package com.chat.app.controller;

import com.chat.app.dto.ApiResponse;
import com.chat.app.dto.MessageResponse;
import com.chat.app.dto.MessageUpdateRequest;
import com.chat.app.model.Message;
import com.chat.app.security.UserPrincipal;
import com.chat.app.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
@Slf4j
public class MessageControllerREST {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    @PutMapping("/{messageId}")
    public ResponseEntity<MessageResponse> editMessage(
            @PathVariable UUID messageId,
            @Valid @RequestBody MessageUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        log.info("Request received to edit message: {} by user: {}", messageId, userPrincipal.getUsername());
        
        Message message = messageService.editMessage(messageId, request, userPrincipal.getId());
        MessageResponse response = messageService.mapToResponse(message);

        // Broadcast to WebSocket subscribers to sync change in real-time
        String destination = "/topic/room." + message.getRoom().getId();
        messagingTemplate.convertAndSend(destination, response);
        log.debug("Broadcasted updated message {} to topic {}", messageId, destination);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<ApiResponse> deleteMessage(
            @PathVariable UUID messageId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        log.info("Request received to delete message: {} by user: {}", messageId, userPrincipal.getUsername());

        Message message = messageService.deleteMessage(messageId, userPrincipal.getId());
        MessageResponse response = messageService.mapToResponse(message);

        // Broadcast to WebSocket subscribers to sync soft-delete in real-time
        String destination = "/topic/room." + message.getRoom().getId();
        messagingTemplate.convertAndSend(destination, response);
        log.debug("Broadcasted soft-deleted message {} to topic {}", messageId, destination);

        return ResponseEntity.ok(new ApiResponse(true, "Message deleted successfully."));
    }

    @GetMapping("/{messageId}/thread")
    public ResponseEntity<java.util.List<MessageResponse>> getThreadReplies(
            @PathVariable UUID messageId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        log.info("Request received to fetch thread replies for message: {} by user: {}", messageId, userPrincipal.getUsername());
        
        java.util.List<MessageResponse> responses = messageService.getThreadReplies(messageId, userPrincipal.getId());
        return ResponseEntity.ok(responses);
    }
}
