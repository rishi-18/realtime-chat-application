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
}
