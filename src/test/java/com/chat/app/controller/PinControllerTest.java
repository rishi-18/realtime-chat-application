package com.chat.app.controller;

import com.chat.app.dto.MessageResponse;
import com.chat.app.dto.PinToggleResponse;
import com.chat.app.security.UserPrincipal;
import com.chat.app.service.MessageService;
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

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PinControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private PinController pinController;

    private UserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(pinController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        userPrincipal = new UserPrincipal(UUID.randomUUID(), "testuser", "test@test.com", "password", Collections.emptyList());
    }

    @Test
    void togglePinMessage_Success() throws Exception {
        // Arrange
        UUID messageId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        PinToggleResponse response = PinToggleResponse.builder()
                .messageId(messageId)
                .roomId(roomId)
                .pinned(true)
                .pinnedByUsername("testuser")
                .pinnedAt(Instant.now())
                .build();

        when(messageService.togglePinMessage(eq(messageId), any(UUID.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/v1/messages/" + messageId + "/pin")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinned").value(true))
                .andExpect(jsonPath("$.pinnedByUsername").value("testuser"));
    }

    @Test
    void getPinnedMessages_Success() throws Exception {
        // Arrange
        UUID roomId = UUID.randomUUID();
        MessageResponse msgResponse = MessageResponse.builder()
                .id(UUID.randomUUID())
                .roomId(roomId)
                .content("pinned message text")
                .senderUsername("testuser")
                .isPinned(true)
                .isDeleted(false)
                .timestamp(Instant.now())
                .build();

        when(messageService.getPinnedMessages(eq(roomId), any(UUID.class)))
                .thenReturn(Collections.singletonList(msgResponse));

        // Act & Assert
        mockMvc.perform(get("/api/v1/rooms/" + roomId + "/pins")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("pinned message text"))
                .andExpect(jsonPath("$[0].pinned").value(true));
    }
}
