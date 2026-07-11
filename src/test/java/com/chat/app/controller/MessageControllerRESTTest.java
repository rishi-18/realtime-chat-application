package com.chat.app.controller;

import com.chat.app.dto.MessageResponse;
import com.chat.app.dto.MessageUpdateRequest;
import com.chat.app.model.Message;
import com.chat.app.security.UserPrincipal;
import com.chat.app.service.MessageService;
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

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MessageControllerRESTTest {

    private MockMvc mockMvc;

    @Mock
    private MessageService messageService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private MessageControllerREST messageControllerREST;

    private UserPrincipal userPrincipal;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(messageControllerREST)
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
    void editMessage_Success() throws Exception {
        UUID messageId = UUID.randomUUID();
        MessageUpdateRequest request = new MessageUpdateRequest("new content");

        com.chat.app.model.Room room = com.chat.app.model.Room.builder().id(UUID.randomUUID()).build();
        Message message = Message.builder()
                .id(messageId)
                .room(room)
                .content("new content")
                .isDeleted(false)
                .createdAt(Instant.now())
                .build();

        MessageResponse response = MessageResponse.builder()
                .id(messageId)
                .roomId(room.getId())
                .content("new content")
                .isDeleted(false)
                .timestamp(message.getCreatedAt())
                .build();

        when(messageService.editMessage(eq(messageId), any(MessageUpdateRequest.class), any(UUID.class)))
                .thenReturn(message);
        when(messageService.mapToResponse(any(Message.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/messages/" + messageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .principal(new UsernamePasswordAuthenticationToken(userPrincipal, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("new content"));

        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/room." + room.getId()), any(MessageResponse.class));
    }

    @Test
    void deleteMessage_Success() throws Exception {
        UUID messageId = UUID.randomUUID();
        com.chat.app.model.Room room = com.chat.app.model.Room.builder().id(UUID.randomUUID()).build();
        Message message = Message.builder()
                .id(messageId)
                .room(room)
                .isDeleted(true)
                .createdAt(Instant.now())
                .build();

        MessageResponse response = MessageResponse.builder()
                .id(messageId)
                .roomId(room.getId())
                .isDeleted(true)
                .timestamp(message.getCreatedAt())
                .build();

        when(messageService.deleteMessage(eq(messageId), any(UUID.class))).thenReturn(message);
        when(messageService.mapToResponse(any(Message.class))).thenReturn(response);

        mockMvc.perform(delete("/api/v1/messages/" + messageId)
                        .principal(new UsernamePasswordAuthenticationToken(userPrincipal, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Message deleted successfully."));

        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/room." + room.getId()), any(MessageResponse.class));
    }

    @Test
    void getThreadReplies_Success() throws Exception {
        UUID messageId = UUID.randomUUID();
        MessageResponse replyResponse = MessageResponse.builder()
                .id(UUID.randomUUID())
                .roomId(UUID.randomUUID())
                .content("reply content")
                .parentMessageId(messageId)
                .build();

        when(messageService.getThreadReplies(eq(messageId), any(UUID.class)))
                .thenReturn(Collections.singletonList(replyResponse));

        mockMvc.perform(get("/api/v1/messages/" + messageId + "/thread")
                        .principal(new UsernamePasswordAuthenticationToken(userPrincipal, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("reply content"))
                .andExpect(jsonPath("$[0].parentMessageId").value(messageId.toString()));
    }

    @Test
    void getMessageEditHistory_Success() throws Exception {
        UUID messageId = UUID.randomUUID();
        com.chat.app.dto.MessageRevisionResponse revisionResponse = com.chat.app.dto.MessageRevisionResponse.builder()
                .id(UUID.randomUUID())
                .messageId(messageId)
                .oldContent("old custom content")
                .editedAt(Instant.now())
                .build();

        when(messageService.getMessageEditHistory(eq(messageId), any(UUID.class)))
                .thenReturn(Collections.singletonList(revisionResponse));

        mockMvc.perform(get("/api/v1/messages/" + messageId + "/history")
                        .principal(new UsernamePasswordAuthenticationToken(userPrincipal, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].oldContent").value("old custom content"))
                .andExpect(jsonPath("$[0].messageId").value(messageId.toString()));
    }
}
