package com.chat.app.service;

import com.chat.app.dto.ReactionSyncResponse;
import com.chat.app.model.Message;
import com.chat.app.model.MessageReaction;
import com.chat.app.model.Room;
import com.chat.app.model.User;
import com.chat.app.repository.MessageReactionRepository;
import com.chat.app.repository.MessageRepository;
import com.chat.app.repository.RoomMemberRepository;
import com.chat.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageReactionServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoomMemberRepository roomMemberRepository;

    @Mock
    private MessageReactionRepository messageReactionRepository;

    @InjectMocks
    private MessageReactionService messageReactionService;

    private UUID messageId;
    private UUID userId;
    private UUID roomId;
    private Message message;
    private User user;

    @BeforeEach
    void setUp() {
        messageId = UUID.randomUUID();
        userId = UUID.randomUUID();
        roomId = UUID.randomUUID();

        Room room = Room.builder().id(roomId).build();
        message = Message.builder().id(messageId).room(room).isDeleted(false).build();
        user = User.builder().id(userId).username("reactor").build();
    }

    @Test
    void toggleReaction_AddsReaction_WhenNotExists() {
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)).thenReturn(true);
        when(messageReactionRepository.findByMessageIdAndUserIdAndEmoji(messageId, userId, "🚀")).thenReturn(Optional.empty());

        ReactionSyncResponse response = messageReactionService.toggleReaction(messageId, "🚀", userId);

        assertEquals("ADDED", response.getAction());
        assertEquals("🚀", response.getEmoji());
        assertEquals(userId, response.getUserId());
        verify(messageReactionRepository, times(1)).save(any(MessageReaction.class));
    }

    @Test
    void toggleReaction_RemovesReaction_WhenExists() {
        MessageReaction existingReaction = MessageReaction.builder().message(message).user(user).emoji("🚀").build();

        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)).thenReturn(true);
        when(messageReactionRepository.findByMessageIdAndUserIdAndEmoji(messageId, userId, "🚀")).thenReturn(Optional.of(existingReaction));

        ReactionSyncResponse response = messageReactionService.toggleReaction(messageId, "🚀", userId);

        assertEquals("REMOVED", response.getAction());
        verify(messageReactionRepository, times(1)).delete(existingReaction);
    }

    @Test
    void toggleReaction_ThrowsAccessDeniedException_WhenNotMember() {
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> messageReactionService.toggleReaction(messageId, "🚀", userId));
    }
}
