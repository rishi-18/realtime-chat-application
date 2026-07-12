package com.chat.app.service;

import com.chat.app.dto.RoomCreateRequest;
import com.chat.app.exception.RoomAlreadyExistsException;
import com.chat.app.exception.RoomNotFoundException;
import com.chat.app.model.Room;
import com.chat.app.model.RoomMember;
import com.chat.app.model.RoomMemberId;
import com.chat.app.model.User;
import com.chat.app.model.RoomType;
import com.chat.app.repository.RoomMemberRepository;
import com.chat.app.repository.RoomRepository;
import com.chat.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomMemberRepository roomMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.chat.app.repository.MessageRepository messageRepository;

    @Mock
    private com.chat.app.service.UserBlockService userBlockService;

    private RoomService roomService;

    private User creator;
    private RoomCreateRequest roomCreateRequest;

    @BeforeEach
    void setUp() {
        List<com.chat.app.service.strategy.RoomJoinStrategy> strategies = new java.util.ArrayList<>();
        strategies.add(new com.chat.app.service.strategy.PublicRoomJoinStrategy(roomMemberRepository));
        strategies.add(new com.chat.app.service.strategy.PrivateRoomJoinStrategy(roomMemberRepository, mock(com.chat.app.repository.RoomInviteRepository.class)));
        strategies.add(new com.chat.app.service.strategy.DmRoomJoinStrategy());

        roomService = new RoomService(
                roomRepository,
                roomMemberRepository,
                userRepository,
                messageRepository,
                userBlockService,
                strategies
        );
        creator = User.builder()
                .id(UUID.randomUUID())
                .username("creator")
                .email("creator@example.com")
                .build();

        roomCreateRequest = RoomCreateRequest.builder()
                .name("developer-channel")
                .description("Coding channel")
                .build();
    }

    @Test
    void createRoom_Success() {
        // Arrange
        when(roomRepository.existsByName(roomCreateRequest.getName())).thenReturn(false);
        when(userRepository.findById(creator.getId())).thenReturn(Optional.of(creator));
        
        Room savedRoom = Room.builder()
                .id(UUID.randomUUID())
                .name(roomCreateRequest.getName())
                .description(roomCreateRequest.getDescription())
                .createdBy(creator)
                .build();
        when(roomRepository.save(any(Room.class))).thenReturn(savedRoom);

        // Act
        Room result = roomService.createRoom(roomCreateRequest, creator.getId());

        // Assert
        assertNotNull(result);
        assertEquals(roomCreateRequest.getName(), result.getName());
        assertEquals(creator, result.getCreatedBy());
        verify(roomRepository, times(1)).save(any(Room.class));
        verify(roomMemberRepository, times(1)).save(any(RoomMember.class));
    }

    @Test
    void createRoom_ThrowsRoomAlreadyExistsException() {
        // Arrange
        when(roomRepository.existsByName(roomCreateRequest.getName())).thenReturn(true);

        // Act & Assert
        assertThrows(RoomAlreadyExistsException.class, () -> roomService.createRoom(roomCreateRequest, creator.getId()));
        verify(roomRepository, never()).save(any(Room.class));
        verify(roomMemberRepository, never()).save(any(RoomMember.class));
    }

    @Test
    void joinRoom_Success() {
        // Arrange
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Room room = Room.builder().id(roomId).name("developers").roomType(RoomType.PUBLIC_GROUP).build();
        User user = User.builder().id(userId).username("newcomer").build();

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)).thenReturn(false);

        // Act & Assert
        assertDoesNotThrow(() -> roomService.joinRoom(roomId, userId));
        verify(roomMemberRepository, times(1)).save(any(RoomMember.class));
    }

    @Test
    void joinRoom_ThrowsRoomNotFoundException() {
        // Arrange
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(roomRepository.findById(roomId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RoomNotFoundException.class, () -> roomService.joinRoom(roomId, userId));
        verify(roomMemberRepository, never()).save(any(RoomMember.class));
    }

    @Test
    void joinRoom_ThrowsIllegalArgumentException_WhenAlreadyMember() {
        // Arrange
        UUID roomId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Room room = Room.builder().id(roomId).name("developers").roomType(RoomType.PUBLIC_GROUP).build();
        User user = User.builder().id(userId).username("newcomer").build();

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)).thenReturn(true);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> roomService.joinRoom(roomId, userId));
        verify(roomMemberRepository, never()).save(any(RoomMember.class));
    }

    @Test
    void createOrGetDmRoom_Success_CreatesNewRoom() {
        // Arrange
        UUID creatorId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        User creator = User.builder().id(creatorId).username("creator").build();
        User recipient = User.builder().id(recipientId).username("recipient").build();

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(userRepository.findById(recipientId)).thenReturn(Optional.of(recipient));
        when(roomMemberRepository.findDmRoomBetweenUsers(creatorId, recipientId)).thenReturn(Optional.empty());

        Room savedRoom = Room.builder()
                .id(UUID.randomUUID())
                .name("dm-hashedname")
                .createdBy(creator)
                .roomType(com.chat.app.model.RoomType.DIRECT_MESSAGE)
                .build();
        when(roomRepository.save(any(Room.class))).thenReturn(savedRoom);

        // Act
        Room result = roomService.createOrGetDmRoom(creatorId, recipientId);

        // Assert
        assertNotNull(result);
        assertEquals(com.chat.app.model.RoomType.DIRECT_MESSAGE, result.getRoomType());
        verify(roomRepository, times(1)).save(any(Room.class));
        verify(roomMemberRepository, times(2)).save(any(RoomMember.class));
    }

    @Test
    void createOrGetDmRoom_Success_ReturnsExistingRoom() {
        // Arrange
        UUID creatorId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID existingRoomId = UUID.randomUUID();
        User creator = User.builder().id(creatorId).username("creator").build();
        User recipient = User.builder().id(recipientId).username("recipient").build();

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(userRepository.findById(recipientId)).thenReturn(Optional.of(recipient));
        when(roomMemberRepository.findDmRoomBetweenUsers(creatorId, recipientId)).thenReturn(Optional.of(existingRoomId));

        Room existingRoom = Room.builder()
                .id(existingRoomId)
                .name("dm-hashedname")
                .createdBy(creator)
                .roomType(com.chat.app.model.RoomType.DIRECT_MESSAGE)
                .build();
        when(roomRepository.findById(existingRoomId)).thenReturn(Optional.of(existingRoom));

        // Act
        Room result = roomService.createOrGetDmRoom(creatorId, recipientId);

        // Assert
        assertNotNull(result);
        assertEquals(existingRoomId, result.getId());
        verify(roomRepository, never()).save(any(Room.class));
        verify(roomMemberRepository, never()).save(any(RoomMember.class));
    }

    @Test
    void createOrGetDmRoom_ThrowsIllegalArgumentException_WhenSelfDM() {
        // Arrange
        UUID sameId = UUID.randomUUID();

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> roomService.createOrGetDmRoom(sameId, sameId));
        verify(roomRepository, never()).save(any(Room.class));
    }

    @Test
    void updateLastReadMessage_Success() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        Room room = Room.builder().id(roomId).name("testroom").build();
        User user = User.builder().id(userId).username("testuser").build();
        com.chat.app.model.RoomMemberId memberId = new com.chat.app.model.RoomMemberId(roomId, userId);
        RoomMember member = RoomMember.builder().id(memberId).room(room).user(user).build();

        com.chat.app.model.Message message = com.chat.app.model.Message.builder()
                .id(messageId)
                .room(room)
                .createdAt(java.time.Instant.now())
                .build();

        when(roomMemberRepository.findById(any(com.chat.app.model.RoomMemberId.class))).thenReturn(Optional.of(member));
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));

        // Act
        roomService.updateLastReadMessage(userId, roomId, messageId);

        // Assert
        assertEquals(message, member.getLastReadMessage());
        verify(roomMemberRepository, times(1)).save(member);
    }

    @Test
    void updateLastReadMessage_ThrowsIllegalArgumentException_WhenNotMember() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        when(roomMemberRepository.findById(any(com.chat.app.model.RoomMemberId.class))).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> roomService.updateLastReadMessage(userId, roomId, messageId));
        verify(roomMemberRepository, never()).save(any(RoomMember.class));
    }

    @Test
    void updateLastReadMessage_ThrowsIllegalArgumentException_WhenMessageNotFound() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        Room room = Room.builder().id(roomId).name("testroom").build();
        User user = User.builder().id(userId).username("testuser").build();
        com.chat.app.model.RoomMemberId memberId = new com.chat.app.model.RoomMemberId(roomId, userId);
        RoomMember member = RoomMember.builder().id(memberId).room(room).user(user).build();

        when(roomMemberRepository.findById(any(com.chat.app.model.RoomMemberId.class))).thenReturn(Optional.of(member));
        when(messageRepository.findById(messageId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> roomService.updateLastReadMessage(userId, roomId, messageId));
        verify(roomMemberRepository, never()).save(any(RoomMember.class));
    }

    @Test
    void updateLastReadMessage_ThrowsIllegalArgumentException_WhenMessageDifferentRoom() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID anotherRoomId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        Room room = Room.builder().id(roomId).name("testroom").build();
        Room anotherRoom = Room.builder().id(anotherRoomId).name("another").build();
        User user = User.builder().id(userId).username("testuser").build();
        com.chat.app.model.RoomMemberId memberId = new com.chat.app.model.RoomMemberId(roomId, userId);
        RoomMember member = RoomMember.builder().id(memberId).room(room).user(user).build();

        com.chat.app.model.Message message = com.chat.app.model.Message.builder()
                .id(messageId)
                .room(anotherRoom)
                .build();

        when(roomMemberRepository.findById(any(com.chat.app.model.RoomMemberId.class))).thenReturn(Optional.of(member));
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> roomService.updateLastReadMessage(userId, roomId, messageId));
        verify(roomMemberRepository, never()).save(any(RoomMember.class));
    }

    @Test
    void updateLastReadMessage_Ignored_WhenOlderMessage() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        Room room = Room.builder().id(roomId).name("testroom").build();
        User user = User.builder().id(userId).username("testuser").build();
        com.chat.app.model.RoomMemberId memberId = new com.chat.app.model.RoomMemberId(roomId, userId);

        java.time.Instant now = java.time.Instant.now();
        com.chat.app.model.Message currentRead = com.chat.app.model.Message.builder()
                .id(UUID.randomUUID())
                .room(room)
                .createdAt(now)
                .build();

        RoomMember member = RoomMember.builder().id(memberId).room(room).user(user).lastReadMessage(currentRead).build();

        com.chat.app.model.Message olderMessage = com.chat.app.model.Message.builder()
                .id(messageId)
                .room(room)
                .createdAt(now.minusSeconds(10))
                .build();

        when(roomMemberRepository.findById(any(com.chat.app.model.RoomMemberId.class))).thenReturn(Optional.of(member));
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(olderMessage));

        // Act
        roomService.updateLastReadMessage(userId, roomId, messageId);

        // Assert
        assertEquals(currentRead, member.getLastReadMessage());
        verify(roomMemberRepository, never()).save(any(RoomMember.class));
    }

    @Test
    void updateMemberRole_Success() {
        // Arrange
        UUID roomId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();

        RoomMemberId requesterId = RoomMemberId.builder().roomId(roomId).userId(ownerId).build();
        RoomMember requester = RoomMember.builder().id(requesterId).role(com.chat.app.model.RoomRole.OWNER).build();

        RoomMemberId targetId = RoomMemberId.builder().roomId(roomId).userId(targetUserId).build();
        RoomMember target = RoomMember.builder().id(targetId).role(com.chat.app.model.RoomRole.MEMBER).build();

        when(roomMemberRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(roomMemberRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(roomMemberRepository.save(any(RoomMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        com.chat.app.dto.RoleUpdateResponse response = roomService.updateMemberRole(roomId, targetUserId, com.chat.app.model.RoomRole.MODERATOR, ownerId);

        // Assert
        assertNotNull(response);
        assertEquals("MODERATOR", response.getRole());
        verify(roomMemberRepository, times(1)).save(target);
    }

    @Test
    void updateMemberRole_Forbidden_WhenNotOwner() {
        // Arrange
        UUID roomId = UUID.randomUUID();
        UUID requesterUserId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();

        RoomMemberId requesterId = RoomMemberId.builder().roomId(roomId).userId(requesterUserId).build();
        RoomMember requester = RoomMember.builder().id(requesterId).role(com.chat.app.model.RoomRole.MEMBER).build();

        when(roomMemberRepository.findById(requesterId)).thenReturn(Optional.of(requester));

        // Act & Assert
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> {
            roomService.updateMemberRole(roomId, targetUserId, com.chat.app.model.RoomRole.MODERATOR, requesterUserId);
        });
    }

    @Test
    void kickMember_Success_OwnerKicksModerator() {
        // Arrange
        UUID roomId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID moderatorId = UUID.randomUUID();

        RoomMemberId requesterId = RoomMemberId.builder().roomId(roomId).userId(ownerId).build();
        RoomMember requester = RoomMember.builder().id(requesterId).role(com.chat.app.model.RoomRole.OWNER).build();

        RoomMemberId targetId = RoomMemberId.builder().roomId(roomId).userId(moderatorId).build();
        RoomMember target = RoomMember.builder().id(targetId).role(com.chat.app.model.RoomRole.MODERATOR).build();

        when(roomMemberRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(roomMemberRepository.findById(targetId)).thenReturn(Optional.of(target));

        // Act
        roomService.kickMember(roomId, moderatorId, ownerId);

        // Assert
        verify(roomMemberRepository, times(1)).delete(target);
    }

    @Test
    void kickMember_Forbidden_ModeratorKicksOwner() {
        // Arrange
        UUID roomId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID moderatorId = UUID.randomUUID();

        RoomMemberId requesterId = RoomMemberId.builder().roomId(roomId).userId(moderatorId).build();
        RoomMember requester = RoomMember.builder().id(requesterId).role(com.chat.app.model.RoomRole.MODERATOR).build();

        RoomMemberId targetId = RoomMemberId.builder().roomId(roomId).userId(ownerId).build();
        RoomMember target = RoomMember.builder().id(targetId).role(com.chat.app.model.RoomRole.OWNER).build();

        when(roomMemberRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(roomMemberRepository.findById(targetId)).thenReturn(Optional.of(target));

        // Act & Assert
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> {
            roomService.kickMember(roomId, ownerId, moderatorId);
        });
        verify(roomMemberRepository, never()).delete(any(RoomMember.class));
    }
}
