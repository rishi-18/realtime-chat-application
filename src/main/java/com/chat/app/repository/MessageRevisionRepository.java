package com.chat.app.repository;

import com.chat.app.model.MessageRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRevisionRepository extends JpaRepository<MessageRevision, UUID> {

    List<MessageRevision> findByMessageIdOrderByEditedAtDesc(UUID messageId);
}
