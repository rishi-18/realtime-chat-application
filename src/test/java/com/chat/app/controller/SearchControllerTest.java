package com.chat.app.controller;

import com.chat.app.dto.MessageResponse;
import com.chat.app.security.UserPrincipal;
import com.chat.app.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private SearchController searchController;

    private UserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(searchController)
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .build();
        userPrincipal = new UserPrincipal(UUID.randomUUID(), "testuser", "test@test.com", "password", Collections.emptyList());

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userPrincipal, null, userPrincipal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    void searchMessages_Success() throws Exception {
        UUID roomId = UUID.randomUUID();
        MessageResponse msgResponse = MessageResponse.builder()
                .id(UUID.randomUUID())
                .roomId(roomId)
                .content("hello developer")
                .senderUsername("testuser")
                .timestamp(Instant.now())
                .build();
        Page<MessageResponse> pageResult = new PageImpl<>(Collections.singletonList(msgResponse), org.springframework.data.domain.PageRequest.of(0, 50), 1);

        when(messageService.searchRoomMessages(eq(roomId), any(UUID.class), eq("hello"), anyInt(), anyInt()))
                .thenReturn(pageResult);

        mockMvc.perform(get("/api/v1/rooms/" + roomId + "/messages/search")
                        .param("query", "hello")
                        .principal(new UsernamePasswordAuthenticationToken(userPrincipal, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].content").value("hello developer"));

        verify(messageService, times(1)).searchRoomMessages(eq(roomId), any(UUID.class), eq("hello"), anyInt(), anyInt());
    }

    @Test
    void searchMessages_ThrowsIllegalArgumentException_WhenQueryTooShort() throws Exception {
        UUID roomId = UUID.randomUUID();

        // Standing setup does not have a global exception handler attached by default unless built with ControllerAdvice,
        // so we expect the resolved exception or the status to reflect bad request if we map exceptions,
        // or we can verify the controller throws the exception!
        // In standalone setups, uncaught controller exceptions are rethrown as nested ServletExceptions wrapping the cause.
        try {
            mockMvc.perform(get("/api/v1/rooms/" + roomId + "/messages/search")
                            .param("query", "a")
                            .principal(new UsernamePasswordAuthenticationToken(userPrincipal, null)));
        } catch (Exception e) {
            org.junit.jupiter.api.Assertions.assertTrue(e.getCause() instanceof IllegalArgumentException);
            org.junit.jupiter.api.Assertions.assertEquals("Search query must be at least 2 characters long.", e.getCause().getMessage());
        }
    }
}
