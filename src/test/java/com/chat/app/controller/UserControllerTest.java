package com.chat.app.controller;

import com.chat.app.dto.UserResponse;
import com.chat.app.security.UserPrincipal;
import com.chat.app.service.UserBlockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserBlockService userBlockService;

    @InjectMocks
    private UserController userController;

    private UserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .build();

        userPrincipal = new UserPrincipal(UUID.randomUUID(), "testuser", "test@test.com", "password", Collections.emptyList());

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userPrincipal, null, userPrincipal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    void blockUser_Success() throws Exception {
        UUID targetUserId = UUID.randomUUID();

        doNothing().when(userBlockService).blockUser(any(UUID.class), eq(targetUserId));

        mockMvc.perform(post("/api/v1/users/block/" + targetUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .principal(new UsernamePasswordAuthenticationToken(userPrincipal, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User blocked successfully."));

        verify(userBlockService, times(1)).blockUser(userPrincipal.getId(), targetUserId);
    }

    @Test
    void unblockUser_Success() throws Exception {
        UUID targetUserId = UUID.randomUUID();

        doNothing().when(userBlockService).unblockUser(any(UUID.class), eq(targetUserId));

        mockMvc.perform(post("/api/v1/users/unblock/" + targetUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .principal(new UsernamePasswordAuthenticationToken(userPrincipal, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User unblocked successfully."));

        verify(userBlockService, times(1)).unblockUser(userPrincipal.getId(), targetUserId);
    }

    @Test
    void getBlockedUsers_Success() throws Exception {
        UserResponse response = UserResponse.builder()
                .id(UUID.randomUUID())
                .username("blockeduser")
                .email("blocked@test.com")
                .build();

        when(userBlockService.getBlockedUsers(any(UUID.class))).thenReturn(Collections.singletonList(response));

        mockMvc.perform(get("/api/v1/users/blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .principal(new UsernamePasswordAuthenticationToken(userPrincipal, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("blockeduser"))
                .andExpect(jsonPath("$[0].email").value("blocked@test.com"));

        verify(userBlockService, times(1)).getBlockedUsers(userPrincipal.getId());
    }
}
