package com.chat.app.repository;

import com.chat.app.model.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserBlockRepository extends JpaRepository<UserBlock, UUID> {

    boolean existsByUserIdAndBlockedUserId(UUID userId, UUID blockedUserId);

    Optional<UserBlock> findByUserIdAndBlockedUserId(UUID userId, UUID blockedUserId);

    List<UserBlock> findByUserId(UUID userId);

    @Query("SELECT ub.blockedUser.id FROM UserBlock ub WHERE ub.user.id = :userId")
    List<UUID> findBlockedUserIds(@Param("userId") UUID userId);

    @Query("SELECT ub.user.id FROM UserBlock ub WHERE ub.blockedUser.id = :userId")
    List<UUID> findUsersWhoBlockedMe(@Param("userId") UUID userId);
}
