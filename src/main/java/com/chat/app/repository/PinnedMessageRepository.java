package com.chat.app.repository;

import com.chat.app.model.PinnedMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PinnedMessageRepository extends JpaRepository<PinnedMessage, UUID> {

    Optional<PinnedMessage> findByRoomIdAndMessageId(UUID roomId, UUID messageId);

    List<PinnedMessage> findByRoomId(UUID roomId);

    List<PinnedMessage> findByMessageIdIn(List<UUID> messageIds);
}
