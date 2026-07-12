package com.chat.app.integration;

import com.chat.app.config.TestRedisConfiguration;
import com.chat.app.dto.*;
import com.chat.app.model.*;
import com.chat.app.repository.RoomMemberRepository;
import com.chat.app.repository.RoomRepository;
import com.chat.app.repository.UserRepository;
import com.chat.app.security.UserPrincipal;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class RoomIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomMemberRepository roomMemberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;
    private UserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .username("room_user")
                .email("room@chat.com")
                .passwordHash(passwordEncoder.encode("SecurePass123!"))
                .build();
        testUser = userRepository.save(testUser);

        userPrincipal = new UserPrincipal(testUser.getId(), testUser.getUsername(), testUser.getEmail(), testUser.getPasswordHash(), Collections.emptyList());
    }

    private void addRoomMember(Room room, User user, RoomRole role) {
        RoomMember member = RoomMember.builder()
                .id(RoomMemberId.builder().roomId(room.getId()).userId(user.getId()).build())
                .room(room)
                .user(user)
                .role(role)
                .build();
        roomMemberRepository.save(member);
    }

    @Test
    void createRoom_Success() throws Exception {
        RoomCreateRequest request = RoomCreateRequest.builder()
                .name("developers")
                .description("developers room")
                .build();

        mockMvc.perform(post("/api/v1/rooms")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("developers"));
    }

    @Test
    void joinPublicRoom_Success() throws Exception {
        // Arrange: Create a room first
        Room room = Room.builder()
                .name("General")
                .description("General Discussion")
                .roomType(RoomType.PUBLIC_GROUP)
                .createdBy(testUser)
                .build();
        room = roomRepository.save(room);
        addRoomMember(room, testUser, RoomRole.OWNER);

        // Act & Assert: Create another user and attempt to join general room
        User newcomer = User.builder()
                .username("newcomer")
                .email("new@chat.com")
                .passwordHash(passwordEncoder.encode("SecurePass123!"))
                .build();
        newcomer = userRepository.save(newcomer);
        UserPrincipal newcomerPrincipal = new UserPrincipal(newcomer.getId(), newcomer.getUsername(), newcomer.getEmail(), newcomer.getPasswordHash(), Collections.emptyList());

        mockMvc.perform(post("/api/v1/rooms/" + room.getId() + "/join")
                        .with(user(newcomerPrincipal))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertTrue(roomMemberRepository.existsByIdRoomIdAndIdUserId(room.getId(), newcomer.getId()));
    }

    @Test
    void joinPrivateRoom_ByInviteCode_Success() throws Exception {
        // Arrange: Create a private room
        Room room = Room.builder()
                .name("Secret Project")
                .description("Classified room")
                .roomType(RoomType.PRIVATE_GROUP)
                .createdBy(testUser)
                .build();
        room = roomRepository.save(room);
        addRoomMember(room, testUser, RoomRole.OWNER);

        // Generate invite code
        RoomInviteCreateRequest inviteRequest = RoomInviteCreateRequest.builder()
                .maxUses(5)
                .expirationSeconds(3600L)
                .build();

        String inviteJson = mockMvc.perform(post("/api/v1/rooms/" + room.getId() + "/invites")
                        .with(user(userPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inviteRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        RoomInviteResponse inviteResponse = objectMapper.readValue(inviteJson, RoomInviteResponse.class);
        String code = inviteResponse.getCode();

        // Join using invite code
        User invitee = User.builder()
                .username("invitee")
                .email("invitee@chat.com")
                .passwordHash(passwordEncoder.encode("SecurePass123!"))
                .build();
        invitee = userRepository.save(invitee);
        UserPrincipal inviteePrincipal = new UserPrincipal(invitee.getId(), invitee.getUsername(), invitee.getEmail(), invitee.getPasswordHash(), Collections.emptyList());

        mockMvc.perform(post("/api/v1/rooms/join-by-invite/" + code)
                        .with(user(inviteePrincipal))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertTrue(roomMemberRepository.existsByIdRoomIdAndIdUserId(room.getId(), invitee.getId()));
    }
}
