package com.chat.app.repository;

import com.chat.app.model.RoomMember;
import com.chat.app.model.RoomMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RoomMemberRepository extends JpaRepository<RoomMember, RoomMemberId> {

    boolean existsByIdRoomIdAndIdUserId(UUID roomId, UUID userId);

    List<RoomMember> findByIdUserId(UUID userId);

    List<RoomMember> findByIdRoomId(UUID roomId);

    @org.springframework.data.jpa.repository.Query("SELECT rm1.room.id FROM RoomMember rm1 JOIN RoomMember rm2 ON rm1.room.id = rm2.room.id " +
            "WHERE rm1.room.roomType = com.chat.app.model.RoomType.DIRECT_MESSAGE " +
            "AND rm1.user.id = :user1Id AND rm2.user.id = :user2Id")
    java.util.Optional<UUID> findDmRoomBetweenUsers(
            @org.springframework.data.repository.query.Param("user1Id") UUID user1Id,
            @org.springframework.data.repository.query.Param("user2Id") UUID user2Id
    );
}
