package com.chat.app.service.strategy;

import com.chat.app.model.*;
import com.chat.app.repository.RoomInviteRepository;
import com.chat.app.repository.RoomMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomJoinStrategyTest {

    @Mock
    private RoomMemberRepository roomMemberRepository;

    @Mock
    private RoomInviteRepository roomInviteRepository;

    private PublicRoomJoinStrategy publicStrategy;
    private PrivateRoomJoinStrategy privateStrategy;
    private DmRoomJoinStrategy dmStrategy;

    private Room publicRoom;
    private Room privateRoom;
    private Room dmRoom;
    private User user;

    @BeforeEach
    void setUp() {
        publicStrategy = new PublicRoomJoinStrategy(roomMemberRepository);
        privateStrategy = new PrivateRoomJoinStrategy(roomMemberRepository, roomInviteRepository);
        dmStrategy = new DmRoomJoinStrategy();

        user = User.builder().id(UUID.randomUUID()).username("testuser").build();

        publicRoom = Room.builder().id(UUID.randomUUID()).roomType(RoomType.PUBLIC_GROUP).build();
        privateRoom = Room.builder().id(UUID.randomUUID()).roomType(RoomType.PRIVATE_GROUP).build();
        dmRoom = Room.builder().id(UUID.randomUUID()).roomType(RoomType.DIRECT_MESSAGE).build();
    }

    @Test
    void testSupports() {
        assertTrue(publicStrategy.supports(RoomType.PUBLIC_GROUP));
        assertFalse(publicStrategy.supports(RoomType.PRIVATE_GROUP));

        assertTrue(privateStrategy.supports(RoomType.PRIVATE_GROUP));
        assertFalse(privateStrategy.supports(RoomType.PUBLIC_GROUP));

        assertTrue(dmStrategy.supports(RoomType.DIRECT_MESSAGE));
        assertFalse(dmStrategy.supports(RoomType.PUBLIC_GROUP));
    }

    @Test
    void publicJoin_Success() {
        when(roomMemberRepository.existsByIdRoomIdAndIdUserId(publicRoom.getId(), user.getId())).thenReturn(false);

        assertDoesNotThrow(() -> publicStrategy.join(publicRoom, user, null));
        verify(roomMemberRepository, times(1)).save(any(RoomMember.class));
    }

    @Test
    void privateJoin_ThrowsAccessDenied_WhenNoCode() {
        assertThrows(AccessDeniedException.class, () -> privateStrategy.join(privateRoom, user, null));
    }

    @Test
    void privateJoin_Success_WithValidCode() {
        String code = "valid-code";
        RoomInvite invite = RoomInvite.builder()
                .id(UUID.randomUUID())
                .room(privateRoom)
                .code(code)
                .build();

        when(roomMemberRepository.existsByIdRoomIdAndIdUserId(privateRoom.getId(), user.getId())).thenReturn(false);
        when(roomInviteRepository.findByCode(code)).thenReturn(Optional.of(invite));
        when(roomInviteRepository.incrementUsesAtomic(invite.getId())).thenReturn(1);

        assertDoesNotThrow(() -> privateStrategy.join(privateRoom, user, code));
        verify(roomMemberRepository, times(1)).save(any(RoomMember.class));
    }

    @Test
    void dmJoin_AlwaysThrowsAccessDenied() {
        assertThrows(AccessDeniedException.class, () -> dmStrategy.join(dmRoom, user, null));
    }
}
