package com.chat.app.controller;

import com.chat.app.dto.ReactionRequest;
import com.chat.app.dto.ReactionSyncResponse;
import com.chat.app.security.UserPrincipal;
import com.chat.app.service.MessageReactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReactionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MessageReactionService messageReactionService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ReactionController reactionController;

    private UserPrincipal userPrincipal;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(reactionController)
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .build();
        userPrincipal = new UserPrincipal(UUID.randomUUID(), "testuser", "test@test.com", "password", Collections.emptyList());
        objectMapper = new ObjectMapper();

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userPrincipal, null, userPrincipal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    void toggleReaction_Success() throws Exception {
        UUID messageId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        ReactionRequest request = new ReactionRequest("🚀");

        ReactionSyncResponse response = ReactionSyncResponse.builder()
                .messageId(messageId)
                .roomId(roomId)
                .emoji("🚀")
                .action("ADDED")
                .userId(userPrincipal.getId())
                .username(userPrincipal.getUsername())
                .build();

        when(messageReactionService.toggleReaction(eq(messageId), eq("🚀"), any(UUID.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/messages/" + messageId + "/reactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .principal(new UsernamePasswordAuthenticationToken(userPrincipal, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emoji").value("🚀"))
                .andExpect(jsonPath("$.action").value("ADDED"))
                .andExpect(jsonPath("$.roomId").value(roomId.toString()));

        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/room." + roomId), any(ReactionSyncResponse.class));
    }
}
