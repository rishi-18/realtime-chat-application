package com.chat.app.service;

import com.chat.app.dto.MessageRevisionResponse;
import com.chat.app.model.Message;
import com.chat.app.model.MessageRevision;
import com.chat.app.model.Room;
import com.chat.app.repository.MessageRepository;
import com.chat.app.repository.MessageRevisionRepository;
import com.chat.app.repository.RoomMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageRevisionServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private RoomMemberRepository roomMemberRepository;

    @Mock
    private MessageRevisionRepository messageRevisionRepository;

    @InjectMocks
    private MessageRevisionService messageRevisionService;

    private UUID messageId;
    private UUID userId;
    private UUID roomId;
    private Message message;

    @BeforeEach
    void setUp() {
        messageId = UUID.randomUUID();
        userId = UUID.randomUUID();
        roomId = UUID.randomUUID();

        Room room = Room.builder().id(roomId).build();
        message = Message.builder().id(messageId).room(room).isDeleted(false).build();
    }

    @Test
    void getMessageEditHistory_Success() {
        MessageRevision revision = MessageRevision.builder()
                .id(UUID.randomUUID())
                .message(message)
                .oldContent("initial text")
                .editedAt(Instant.now())
                .build();

        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(roomMemberRepository.existsByIdRoomIdAndIdUserId(roomId, userId)).thenReturn(true);
        when(messageRevisionRepository.findByMessageIdOrderByEditedAtDesc(messageId)).thenReturn(Collections.singletonList(revision));

        List<MessageRevisionResponse> result = messageRevisionService.getMessageEditHistory(messageId, userId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("initial text", result.get(0).getOldContent());
    }
}
