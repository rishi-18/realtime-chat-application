package com.chat.app.service;

import com.chat.app.dto.MessageRevisionResponse;
import com.chat.app.model.Message;
import com.chat.app.model.MessageRevision;
import com.chat.app.repository.MessageRepository;
import com.chat.app.repository.MessageRevisionRepository;
import com.chat.app.repository.RoomMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageRevisionService {

    private final MessageRepository messageRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final MessageRevisionRepository messageRevisionRepository;

    @Transactional(readOnly = true)
    public List<MessageRevisionResponse> getMessageEditHistory(UUID messageId, UUID userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found with id: " + messageId));

        if (!roomMemberRepository.existsByIdRoomIdAndIdUserId(message.getRoom().getId(), userId)) {
            throw new AccessDeniedException("Cannot fetch edit history. You are not a member of this room.");
        }

        List<MessageRevision> revisions = messageRevisionRepository.findByMessageIdOrderByEditedAtDesc(messageId);
        if (revisions == null || revisions.isEmpty()) {
            return Collections.emptyList();
        }

        return revisions.stream()
                .map(rev -> MessageRevisionResponse.builder()
                        .id(rev.getId())
                        .messageId(messageId)
                        .oldContent(rev.getOldContent())
                        .editedAt(rev.getEditedAt())
                        .build())
                .collect(Collectors.toList());
    }
}
