package com.chat.app.repository;

import com.chat.app.model.MessageMention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageMentionRepository extends JpaRepository<MessageMention, UUID> {

    List<MessageMention> findByMessageIdIn(List<UUID> messageIds);
}
