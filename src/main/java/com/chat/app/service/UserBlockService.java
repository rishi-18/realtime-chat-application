package com.chat.app.service;

import com.chat.app.dto.UserResponse;
import com.chat.app.model.User;
import com.chat.app.model.UserBlock;
import com.chat.app.repository.UserBlockRepository;
import com.chat.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserBlockService {

    private final UserBlockRepository userBlockRepository;
    private final UserRepository userRepository;

    @Transactional
    public void blockUser(UUID userId, UUID targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new IllegalArgumentException("You cannot block yourself.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Target user not found with id: " + targetUserId));

        if (userBlockRepository.existsByUserIdAndBlockedUserId(userId, targetUserId)) {
            log.info("User {} already blocked user {}", userId, targetUserId);
            return;
        }

        UserBlock block = UserBlock.builder()
                .user(user)
                .blockedUser(target)
                .createdAt(Instant.now())
                .build();

        userBlockRepository.save(block);
        log.info("User {} blocked user {} successfully.", userId, targetUserId);
    }

    @Transactional
    public void unblockUser(UUID userId, UUID targetUserId) {
        UserBlock block = userBlockRepository.findByUserIdAndBlockedUserId(userId, targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("You have not blocked this user."));

        userBlockRepository.delete(block);
        log.info("User {} unblocked user {} successfully.", userId, targetUserId);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getBlockedUsers(UUID userId) {
        List<UserBlock> blocks = userBlockRepository.findByUserId(userId);
        return blocks.stream()
                .map(block -> {
                    User blocked = block.getBlockedUser();
                    return UserResponse.builder()
                            .id(blocked.getId())
                            .username(blocked.getUsername())
                            .email(blocked.getEmail())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean isBlockedSymmetrically(UUID userA, UUID userB) {
        return userBlockRepository.existsByUserIdAndBlockedUserId(userA, userB)
                || userBlockRepository.existsByUserIdAndBlockedUserId(userB, userA);
    }
}
