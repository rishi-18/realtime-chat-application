package com.chat.app.service;

import com.chat.app.dto.MessageResponse;
import com.chat.app.dto.PinToggleResponse;
import com.chat.app.mapper.MessageMapper;
import com.chat.app.model.*;
import com.chat.app.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessagePinServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomMemberRepository roomMemberRepository;

    @Mock
    private PinnedMessageRepository pinnedMessageRepository;

    @Mock
    private MessageReactionRepository messageReactionRepository;

    @Mock
    private MessageMentionRepository messageMentionRepository;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private MessagePinService messagePinService;

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
        user = User.builder().id(userId).username("pinner").build();
    }

    @Test
    void togglePinMessage_PinsMessage_WhenNotPinned() {
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)).thenReturn(true);
        when(pinnedMessageRepository.findByRoomIdAndMessageId(roomId, messageId)).thenReturn(Optional.empty());

        PinToggleResponse response = messagePinService.togglePinMessage(messageId, userId);

        assertTrue(response.isPinned());
        assertEquals("pinner", response.getPinnedByUsername());
        verify(pinnedMessageRepository, times(1)).save(any(PinnedMessage.class));
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/room." + roomId), any(com.chat.app.dto.PinSyncResponse.class));
    }

    @Test
    void togglePinMessage_UnpinsMessage_WhenPinned() {
        PinnedMessage existingPin = PinnedMessage.builder().message(message).room(message.getRoom()).pinnedBy(user).build();

        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)).thenReturn(true);
        when(pinnedMessageRepository.findByRoomIdAndMessageId(roomId, messageId)).thenReturn(Optional.of(existingPin));

        PinToggleResponse response = messagePinService.togglePinMessage(messageId, userId);

        assertFalse(response.isPinned());
        verify(pinnedMessageRepository, times(1)).delete(existingPin);
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/room." + roomId), any(com.chat.app.dto.PinSyncResponse.class));
    }

    @Test
    void getPinnedMessages_Success() {
        PinnedMessage pin = PinnedMessage.builder().message(message).room(message.getRoom()).pinnedBy(user).build();
        MessageResponse mappedResponse = MessageResponse.builder().id(messageId).content("pinned content").isPinned(true).build();

        when(roomRepository.existsById(roomId)).thenReturn(true);
        when(roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)).thenReturn(true);
        when(pinnedMessageRepository.findByRoomId(roomId)).thenReturn(Collections.singletonList(pin));
        when(messageReactionRepository.findByMessageIdIn(anyList())).thenReturn(Collections.emptyList());
        when(messageMentionRepository.findByMessageIdIn(anyList())).thenReturn(Collections.emptyList());
        when(messageMapper.mapToResponse(any(Message.class), anyList(), anyList(), eq(true))).thenReturn(mappedResponse);

        List<MessageResponse> result = messagePinService.getPinnedMessages(roomId, userId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("pinned content", result.get(0).getContent());
    }
}
