package com.chat.app.controller;

import com.chat.app.dto.RoleUpdateRequest;
import com.chat.app.dto.RoleUpdateResponse;
import com.chat.app.model.RoomRole;
import com.chat.app.security.UserPrincipal;
import com.chat.app.service.PresenceService;
import com.chat.app.service.RoomService;
import com.chat.app.service.MessageService;
import com.chat.app.repository.RoomMemberRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RoomControllerTest {

    private MockMvc mockMvc;

    @Mock
    private RoomService roomService;

    @Mock
    private MessageService messageService;

    @Mock
    private RoomMemberRepository roomMemberRepository;

    @Mock
    private PresenceService presenceService;

    @InjectMocks
    private RoomController roomController;

    private UserPrincipal userPrincipal;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(roomController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        userPrincipal = new UserPrincipal(UUID.randomUUID(), "testuser", "test@test.com", "password", Collections.emptyList());
    }

    @Test
    void updateMemberRole_Success() throws Exception {
        // Arrange
        UUID roomId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        RoleUpdateRequest request = RoleUpdateRequest.builder().role(RoomRole.MODERATOR).build();
        RoleUpdateResponse response = RoleUpdateResponse.builder()
                .roomId(roomId)
                .userId(targetUserId)
                .role("MODERATOR")
                .build();

        when(roomService.updateMemberRole(eq(roomId), eq(targetUserId), eq(RoomRole.MODERATOR), any(UUID.class)))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(put("/api/v1/rooms/" + roomId + "/members/" + targetUserId + "/role")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MODERATOR"));
    }

    @Test
    void kickMember_Success() throws Exception {
        // Arrange
        UUID roomId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();

        doNothing().when(roomService).kickMember(eq(roomId), eq(targetUserId), any(UUID.class));

        // Act & Assert
        mockMvc.perform(delete("/api/v1/rooms/" + roomId + "/members/" + targetUserId)
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User kicked successfully."));
    }
}
