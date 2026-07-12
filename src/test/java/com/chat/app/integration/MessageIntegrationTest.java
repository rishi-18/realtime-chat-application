package com.chat.app.integration;

import com.chat.app.config.TestRedisConfiguration;
import com.chat.app.dto.MessageUpdateRequest;
import com.chat.app.dto.ReactionRequest;
import com.chat.app.dto.RoomCreateRequest;
import com.chat.app.model.*;
import com.chat.app.repository.*;
import com.chat.app.security.UserPrincipal;
import com.chat.app.service.MessageService;
import com.chat.app.dto.MessageSendRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class MessageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomMemberRepository roomMemberRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private MessageReactionRepository messageReactionRepository;

    @Autowired
    private PinnedMessageRepository pinnedMessageRepository;

    @Autowired
    private MessageService messageService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;
    private UserPrincipal userPrincipal;
    private Room room;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .username("msg_user")
                .email("msg@chat.com")
                .passwordHash(passwordEncoder.encode("SecurePass123!"))
                .build();
        testUser = userRepository.save(testUser);

        userPrincipal = new UserPrincipal(testUser.getId(), testUser.getUsername(), testUser.getEmail(), testUser.getPasswordHash(), Collections.emptyList());

        room = Room.builder()
                .name("testroom")
                .roomType(RoomType.PUBLIC_GROUP)
                .createdBy(testUser)
                .build();
        room = roomRepository.save(room);

        // Add user as OWNER room member
        RoomMember member = RoomMember.builder()
                .id(RoomMemberId.builder().roomId(room.getId()).userId(testUser.getId()).build())
                .room(room)
                .user(testUser)
                .role(RoomRole.OWNER)
                .build();
        roomMemberRepository.save(member);
    }

    @Test
    void editMessage_Integration_Success() throws Exception {
        // Arrange
        MessageSendRequest sendRequest = MessageSendRequest.builder()
                .roomId(room.getId())
                .content("original message text")
                .build();
        Message message = messageService.saveMessage(sendRequest, testUser.getId());

        MessageUpdateRequest updateRequest = new MessageUpdateRequest("updated message text");

        // Act & Assert
        mockMvc.perform(put("/api/v1/messages/" + message.getId())
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("updated message text"));

        Message updated = messageRepository.findById(message.getId()).orElseThrow();
        assertEquals("updated message text", updated.getContent());
    }

    @Test
    void toggleReaction_Integration_Success() throws Exception {
        // Arrange
        MessageSendRequest sendRequest = MessageSendRequest.builder()
                .roomId(room.getId())
                .content("react to this")
                .build();
        Message message = messageService.saveMessage(sendRequest, testUser.getId());

        ReactionRequest request = new ReactionRequest("👍");

        // Act & Assert (Add Reaction)
        mockMvc.perform(post("/api/v1/messages/" + message.getId() + "/reactions")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emoji").value("👍"))
                .andExpect(jsonPath("$.action").value("ADDED"));

        assertTrue(messageReactionRepository.findByMessageIdAndUserIdAndEmoji(message.getId(), testUser.getId(), "👍").isPresent());

        // Act & Assert (Remove Reaction)
        mockMvc.perform(post("/api/v1/messages/" + message.getId() + "/reactions")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emoji").value("👍"))
                .andExpect(jsonPath("$.action").value("REMOVED"));

        assertFalse(messageReactionRepository.findByMessageIdAndUserIdAndEmoji(message.getId(), testUser.getId(), "👍").isPresent());
    }

    @Test
    void togglePin_Integration_Success() throws Exception {
        // Arrange
        MessageSendRequest sendRequest = MessageSendRequest.builder()
                .roomId(room.getId())
                .content("pin this")
                .build();
        Message message = messageService.saveMessage(sendRequest, testUser.getId());

        // Act & Assert (Pin Message)
        mockMvc.perform(post("/api/v1/messages/" + message.getId() + "/pin")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinned").value(true));

        assertTrue(pinnedMessageRepository.findByRoomIdAndMessageId(room.getId(), message.getId()).isPresent());

        // Act & Assert (Unpin Message)
        mockMvc.perform(post("/api/v1/messages/" + message.getId() + "/pin")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinned").value(false));

        assertFalse(pinnedMessageRepository.findByRoomIdAndMessageId(room.getId(), message.getId()).isPresent());
    }
}
