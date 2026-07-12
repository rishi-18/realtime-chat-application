package com.chat.app.repository;

import com.chat.app.model.RoomInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomInviteRepository extends JpaRepository<RoomInvite, UUID> {

    Optional<RoomInvite> findByCode(String code);

    @Modifying
    @Query("UPDATE RoomInvite r SET r.uses = r.uses + 1 WHERE r.id = :id AND (r.maxUses IS NULL OR r.uses < r.maxUses)")
    int incrementUsesAtomic(@Param("id") UUID id);
}
