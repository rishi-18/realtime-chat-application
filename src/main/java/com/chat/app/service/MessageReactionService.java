package com.chat.app.service;

import com.chat.app.dto.ReactionSyncResponse;
import com.chat.app.model.Message;
import com.chat.app.model.MessageReaction;
import com.chat.app.model.User;
import com.chat.app.repository.MessageReactionRepository;
import com.chat.app.repository.MessageRepository;
import com.chat.app.repository.RoomMemberRepository;
import com.chat.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageReactionService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final MessageReactionRepository messageReactionRepository;

    @Transactional
    public ReactionSyncResponse toggleReaction(UUID messageId, String emoji, UUID userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found with id: " + messageId));

        if (message.isDeleted()) {
            throw new IllegalArgumentException("Cannot react to a soft-deleted message.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        // Enforce membership constraint
        if (!roomMemberRepository.existsByIdRoomIdAndIdUserId(message.getRoom().getId(), userId)) {
            throw new AccessDeniedException("Cannot react to message. You are not a member of this room.");
        }

        Optional<MessageReaction> existing =
                messageReactionRepository.findByMessageIdAndUserIdAndEmoji(messageId, userId, emoji);

        String action;
        if (existing.isPresent()) {
            messageReactionRepository.delete(existing.get());
            action = "REMOVED";
        } else {
            MessageReaction reaction = MessageReaction.builder()
                    .message(message)
                    .user(user)
                    .emoji(emoji)
                    .build();
            messageReactionRepository.save(reaction);
            action = "ADDED";
        }

        return ReactionSyncResponse.builder()
                .messageId(messageId)
                .roomId(message.getRoom().getId())
                .emoji(emoji)
                .action(action)
                .userId(userId)
                .username(user.getUsername())
                .build();
    }
}
