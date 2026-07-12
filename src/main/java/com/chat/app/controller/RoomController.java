package com.chat.app.controller;

import com.chat.app.dto.*;
import com.chat.app.model.Message;
import com.chat.app.model.Room;
import com.chat.app.security.UserPrincipal;
import com.chat.app.service.MessageService;
import com.chat.app.service.RoomService;
import com.chat.app.service.PresenceService;
import com.chat.app.repository.RoomMemberRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final MessageService messageService;
    private final RoomMemberRepository roomMemberRepository;
    private final PresenceService presenceService;
    private final com.chat.app.service.RoomInviteService roomInviteService;

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(
            @Valid @RequestBody RoomCreateRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Room room = roomService.createRoom(request, userPrincipal.getId());
        RoomResponse response = RoomResponse.builder()
                .id(room.getId())
                .name(room.getName())
                .description(room.getDescription())
                .roomType(room.getRoomType().name())
                .createdBy(room.getCreatedBy().getId())
                .createdAt(room.getCreatedAt())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<RoomResponse>> getAllRooms() {
        List<RoomResponse> responses = roomService.getAllRooms().stream()
                .map(room -> RoomResponse.builder()
                        .id(room.getId())
                        .name(room.getName())
                        .description(room.getDescription())
                        .roomType(room.getRoomType().name())
                        .createdBy(room.getCreatedBy().getId())
                        .createdAt(room.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<ApiResponse> joinRoom(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        roomService.joinRoom(roomId, userPrincipal.getId());
        return ResponseEntity.ok(new ApiResponse(true, "Joined room successfully."));
    }

    @GetMapping("/{roomId}/messages")
    public ResponseEntity<Page<MessageResponse>> getMessageHistory(
            @PathVariable UUID roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        // Guard against memory-exhaustion (OOM) vector: ceiling cap maximum page size to 100
        int sanitizedSize = Math.max(1, Math.min(size, 100));
        Page<MessageResponse> responses = messageService.getMessageHistoryResponses(roomId, userPrincipal.getId(), page, sanitizedSize);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{roomId}/presence")
    public ResponseEntity<List<PresenceResponse>> getRoomPresence(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        if (!roomService.isUserMemberOfRoom(roomId, userPrincipal.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied. You are not a member of this chat room.");
        }

        List<com.chat.app.model.RoomMember> members = roomMemberRepository.findByIdRoomId(roomId);
        List<PresenceResponse> responses = members.stream()
                .map(member -> {
                    UUID userId = member.getUser().getId();
                    String username = member.getUser().getUsername();
                    boolean isOnline = presenceService.isUserOnline(userId);
                    return PresenceResponse.builder()
                            .userId(userId)
                            .username(username)
                            .status(isOnline ? "ONLINE" : "OFFLINE")
                            .build();
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @PostMapping("/dm")
    public ResponseEntity<RoomResponse> createOrGetDm(
            @Valid @RequestBody DmRoomCreateRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        Room room = roomService.createOrGetDmRoom(userPrincipal.getId(), request.getRecipientId());
        RoomResponse response = RoomResponse.builder()
                .id(room.getId())
                .name(room.getName())
                .description(room.getDescription())
                .roomType(room.getRoomType().name())
                .createdBy(room.getCreatedBy().getId())
                .createdAt(room.getCreatedAt())
                .build();
        return ResponseEntity.ok(response);
    }
    @PutMapping("/{roomId}/members/{userId}/role")
    public ResponseEntity<RoleUpdateResponse> updateMemberRole(
            @PathVariable UUID roomId,
            @PathVariable UUID userId,
            @Valid @RequestBody RoleUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        RoleUpdateResponse response = roomService.updateMemberRole(roomId, userId, request.getRole(), userPrincipal.getId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{roomId}/members/{userId}")
    public ResponseEntity<ApiResponse> kickMember(
            @PathVariable UUID roomId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        roomService.kickMember(roomId, userId, userPrincipal.getId());
        return ResponseEntity.ok(new ApiResponse(true, "User kicked successfully."));
    }

    @PostMapping("/{roomId}/invites")
    public ResponseEntity<com.chat.app.dto.RoomInviteResponse> createInvite(
            @PathVariable UUID roomId,
            @Valid @RequestBody com.chat.app.dto.RoomInviteCreateRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        com.chat.app.dto.RoomInviteResponse response = roomInviteService.createInvite(roomId, request, userPrincipal.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/join-by-invite/{code}")
    public ResponseEntity<ApiResponse> joinRoomByInvite(
            @PathVariable String code,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        roomInviteService.joinRoomByInvite(code, userPrincipal.getId());
        return ResponseEntity.ok(new ApiResponse(true, "Successfully joined the room."));
    }
}
