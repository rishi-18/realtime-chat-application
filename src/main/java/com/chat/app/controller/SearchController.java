package com.chat.app.controller;

import com.chat.app.dto.MessageResponse;
import com.chat.app.security.UserPrincipal;
import com.chat.app.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
@Slf4j
public class SearchController {

    private final MessageService messageService;

    @GetMapping("/{roomId}/messages/search")
    public ResponseEntity<Page<MessageResponse>> searchRoomMessages(
            @PathVariable UUID roomId,
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        log.info("Request received to search messages in room {} by user {}. Query: {}", 
                roomId, userPrincipal.getUsername(), query);

        if (query == null || query.trim().length() < 2) {
            throw new IllegalArgumentException("Search query must be at least 2 characters long.");
        }

        int sanitizedSize = Math.max(1, Math.min(size, 100));
        Page<MessageResponse> results = messageService.searchRoomMessages(
                roomId, userPrincipal.getId(), query.trim(), page, sanitizedSize
        );

        return ResponseEntity.ok(results);
    }
}
