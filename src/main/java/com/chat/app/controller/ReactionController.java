package com.chat.app.controller;

import com.chat.app.dto.ReactionRequest;
import com.chat.app.dto.ReactionSyncResponse;
import com.chat.app.security.UserPrincipal;
import com.chat.app.service.MessageReactionService;
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
public class ReactionController {

    private final MessageReactionService messageReactionService;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping("/{messageId}/reactions")
    public ResponseEntity<ReactionSyncResponse> toggleReaction(
            @PathVariable UUID messageId,
            @Valid @RequestBody ReactionRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        log.info("Request to toggle reaction {} on message {} by user {}", 
                request.getEmoji(), messageId, userPrincipal.getUsername());

        ReactionSyncResponse response = messageReactionService.toggleReaction(messageId, request.getEmoji(), userPrincipal.getId());

        // Broadcast reaction toggle sync to websocket subscribers
        String destination = "/topic/room." + response.getRoomId();
        messagingTemplate.convertAndSend(destination, response);
        log.debug("Broadcasted reaction sync to topic {}", destination);

        return ResponseEntity.ok(response);
    }
}
