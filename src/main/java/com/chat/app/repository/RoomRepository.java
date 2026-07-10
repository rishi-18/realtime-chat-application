package com.chat.app.repository;

import com.chat.app.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.chat.app.model.RoomType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {

    Optional<Room> findByName(String name);

    boolean existsByName(String name);

    List<Room> findByRoomType(RoomType roomType);
}
