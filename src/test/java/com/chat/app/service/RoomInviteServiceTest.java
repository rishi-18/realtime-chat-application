package com.chat.app.service;

import com.chat.app.dto.RoomInviteCreateRequest;
import com.chat.app.dto.RoomInviteResponse;
import com.chat.app.model.*;
import com.chat.app.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomInviteServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomMemberRepository roomMemberRepository;

    @Mock
    private RoomInviteRepository roomInviteRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RoomInviteService roomInviteService;

    private User creator;
    private Room room;
    private RoomMember ownerMember;

    @BeforeEach
    void setUp() {
        creator = User.builder()
                .id(UUID.randomUUID())
                .username("creator")
                .email("creator@example.com")
                .build();

        room = Room.builder()
                .id(UUID.randomUUID())
                .name("Private Room")
                .roomType(RoomType.PRIVATE_GROUP)
                .build();

        ownerMember = RoomMember.builder()
                .id(new RoomMemberId(room.getId(), creator.getId()))
                .room(room)
                .user(creator)
                .role(RoomRole.OWNER)
                .build();
    }

    @Test
    void createInvite_Success_WhenOwner() {
        // Arrange
        RoomInviteCreateRequest request = new RoomInviteCreateRequest(5, 3600L);
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(roomMemberRepository.findById(new RoomMemberId(room.getId(), creator.getId())))
                .thenReturn(Optional.of(ownerMember));
        when(userRepository.findById(creator.getId())).thenReturn(Optional.of(creator));
        
        when(roomInviteRepository.save(any(RoomInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        RoomInviteResponse response = roomInviteService.createInvite(room.getId(), request, creator.getId());

        // Assert
        assertNotNull(response);
        assertEquals(room.getId(), response.getRoomId());
        assertEquals(5, response.getMaxUses());
        assertNotNull(response.getCode());
        verify(roomInviteRepository, times(1)).save(any(RoomInvite.class));
    }

    @Test
    void createInvite_ThrowsAccessDenied_WhenNotOwnerOrModerator() {
        // Arrange
        RoomMember regularMember = RoomMember.builder()
                .id(new RoomMemberId(room.getId(), creator.getId()))
                .room(room)
                .user(creator)
                .role(RoomRole.MEMBER)
                .build();

        RoomInviteCreateRequest request = new RoomInviteCreateRequest(5, 3600L);
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(roomMemberRepository.findById(new RoomMemberId(room.getId(), creator.getId())))
                .thenReturn(Optional.of(regularMember));

        // Act & Assert
        assertThrows(AccessDeniedException.class, () -> {
            roomInviteService.createInvite(room.getId(), request, creator.getId());
        });
    }

    @Test
    void joinRoomByInvite_Success() {
        // Arrange
        String code = "abcdefgh";
        RoomInvite invite = RoomInvite.builder()
                .id(UUID.randomUUID())
                .room(room)
                .code(code)
                .maxUses(10)
                .uses(0)
                .createdAt(Instant.now())
                .build();

        User joiningUser = User.builder()
                .id(UUID.randomUUID())
                .username("joining")
                .build();

        when(roomInviteRepository.findByCode(code)).thenReturn(Optional.of(invite));
        when(roomMemberRepository.findById(new RoomMemberId(room.getId(), joiningUser.getId())))
                .thenReturn(Optional.empty());
        when(userRepository.findById(joiningUser.getId())).thenReturn(Optional.of(joiningUser));
        when(roomInviteRepository.incrementUsesAtomic(invite.getId())).thenReturn(1);

        // Act
        roomInviteService.joinRoomByInvite(code, joiningUser.getId());

        // Assert
        verify(roomMemberRepository, times(1)).save(any(RoomMember.class));
    }

    @Test
    void joinRoomByInvite_ThrowsException_WhenLimitReached() {
        // Arrange
        String code = "abcdefgh";
        RoomInvite invite = RoomInvite.builder()
                .id(UUID.randomUUID())
                .room(room)
                .code(code)
                .maxUses(5)
                .uses(5)
                .createdAt(Instant.now())
                .build();

        User joiningUser = User.builder()
                .id(UUID.randomUUID())
                .username("joining")
                .build();

        when(roomInviteRepository.findByCode(code)).thenReturn(Optional.of(invite));
        when(roomMemberRepository.findById(new RoomMemberId(room.getId(), joiningUser.getId())))
                .thenReturn(Optional.empty());
        when(userRepository.findById(joiningUser.getId())).thenReturn(Optional.of(joiningUser));
        when(roomInviteRepository.incrementUsesAtomic(invite.getId())).thenReturn(0);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            roomInviteService.joinRoomByInvite(code, joiningUser.getId());
        });
    }
}
