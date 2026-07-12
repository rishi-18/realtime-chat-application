package com.chat.app.mapper;

import com.chat.app.dto.AttachmentResponse;
import com.chat.app.dto.MessageResponse;
import com.chat.app.dto.ReactionResponse;
import com.chat.app.model.Message;
import com.chat.app.model.MessageMention;
import com.chat.app.model.MessageReaction;
import com.chat.app.repository.PinnedMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MessageMapper {

    private final PinnedMessageRepository pinnedMessageRepository;

    public MessageResponse mapToResponse(Message message) {
        boolean isPinned = pinnedMessageRepository.findByRoomIdAndMessageId(
                message.getRoom().getId(),
                message.getId()
        ).isPresent();
        return mapToResponse(message, Collections.emptyList(), Collections.emptyList(), isPinned);
    }

    public MessageResponse mapToResponse(Message message, List<MessageReaction> reactions) {
        boolean isPinned = pinnedMessageRepository.findByRoomIdAndMessageId(
                message.getRoom().getId(),
                message.getId()
        ).isPresent();
        return mapToResponse(message, reactions, Collections.emptyList(), isPinned);
    }

    public MessageResponse mapToResponse(
            Message message,
            List<MessageReaction> reactions,
            List<MessageMention> mentions,
            boolean isPinned) {
        List<AttachmentResponse> attachments = null;
        if (message.getAttachments() != null) {
            attachments = message.getAttachments().stream()
                    .map(att -> AttachmentResponse.builder()
                            .id(att.getId())
                            .fileName(att.getFileName())
                            .fileUrl(att.getFileUrl())
                            .fileType(att.getFileType())
                            .fileSize(att.getFileSize())
                            .build())
                    .collect(Collectors.toList());
        }

        List<ReactionResponse> reactionResponses = null;
        if (reactions != null && !reactions.isEmpty()) {
            reactionResponses = reactions.stream()
                    .map(r -> ReactionResponse.builder()
                            .userId(r.getUser().getId())
                            .username(r.getUser().getUsername())
                            .emoji(r.getEmoji())
                            .build())
                    .collect(Collectors.toList());
        }

        List<String> mentionedUsernames = null;
        if (mentions != null && !mentions.isEmpty()) {
            mentionedUsernames = mentions.stream()
                    .map(m -> m.getUser().getUsername())
                    .collect(Collectors.toList());
        }

        return MessageResponse.builder()
                .id(message.getId())
                .roomId(message.getRoom().getId())
                .senderId(message.getSender() != null ? message.getSender().getId() : null)
                .senderUsername(message.getSender() != null ? message.getSender().getUsername() : "Deleted User")
                .content(message.getContent())
                .attachments(attachments)
                .reactions(reactionResponses)
                .mentionedUsernames(mentionedUsernames)
                .isPinned(isPinned)
                .parentMessageId(message.getParentMessage() != null ? message.getParentMessage().getId() : null)
                .isDeleted(message.isDeleted())
                .timestamp(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
                .build();
    }
}
