package com.chat.app.controller;

import com.chat.app.dto.MessageResponse;
import com.chat.app.dto.PinToggleResponse;
import com.chat.app.security.UserPrincipal;
import com.chat.app.service.MessagePinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class PinController {

    private final MessagePinService messagePinService;

    @PostMapping("/messages/{messageId}/pin")
    public ResponseEntity<PinToggleResponse> togglePinMessage(
            @PathVariable UUID messageId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Request received to toggle pin status on message: {} by user: {}", messageId, userPrincipal.getUsername());
        PinToggleResponse response = messagePinService.togglePinMessage(messageId, userPrincipal.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/rooms/{roomId}/pins")
    public ResponseEntity<List<MessageResponse>> getPinnedMessages(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Request received to fetch pinned messages in room: {} by user: {}", roomId, userPrincipal.getUsername());
        List<MessageResponse> responses = messagePinService.getPinnedMessages(roomId, userPrincipal.getId());
        return ResponseEntity.ok(responses);
    }
}
