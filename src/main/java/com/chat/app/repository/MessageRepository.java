package com.chat.app.repository;

import com.chat.app.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    // Fetches message history with paging, joining indexes on room_id and sorting via created_at
    Page<Message> findByRoomId(UUID roomId, Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
        value = "SELECT * FROM messages WHERE room_id = :roomId AND is_deleted = false " +
                "AND to_tsvector('english', coalesce(content, '')) @@ plainto_tsquery('english', :query)",
        countQuery = "SELECT count(*) FROM messages WHERE room_id = :roomId AND is_deleted = false " +
                     "AND to_tsvector('english', coalesce(content, '')) @@ plainto_tsquery('english', :query)",
        nativeQuery = true
    )
    Page<Message> searchRoomMessages(
            @org.springframework.data.repository.query.Param("roomId") UUID roomId,
            @org.springframework.data.repository.query.Param("query") String query,
            Pageable pageable
    );

    java.util.List<Message> findByParentMessageIdOrderByCreatedAtAsc(UUID parentMessageId);
}
