package com.chat.app.controller;

import com.chat.app.dto.ApiResponse;
import com.chat.app.dto.UserResponse;
import com.chat.app.security.UserPrincipal;
import com.chat.app.service.UserBlockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserBlockService userBlockService;

    @PostMapping("/block/{targetUserId}")
    public ResponseEntity<ApiResponse> blockUser(
            @PathVariable UUID targetUserId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Request received from user {} to block user {}", userPrincipal.getId(), targetUserId);
        userBlockService.blockUser(userPrincipal.getId(), targetUserId);
        return ResponseEntity.ok(new ApiResponse(true, "User blocked successfully."));
    }

    @PostMapping("/unblock/{targetUserId}")
    public ResponseEntity<ApiResponse> unblockUser(
            @PathVariable UUID targetUserId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Request received from user {} to unblock user {}", userPrincipal.getId(), targetUserId);
        userBlockService.unblockUser(userPrincipal.getId(), targetUserId);
        return ResponseEntity.ok(new ApiResponse(true, "User unblocked successfully."));
    }

    @GetMapping("/blocks")
    public ResponseEntity<List<UserResponse>> getBlockedUsers(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("Request received to fetch blocked users list for user: {}", userPrincipal.getId());
        List<UserResponse> blocked = userBlockService.getBlockedUsers(userPrincipal.getId());
        return ResponseEntity.ok(blocked);
    }
}
