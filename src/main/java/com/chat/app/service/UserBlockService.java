package com.chat.app.service;

import com.chat.app.dto.UserResponse;
import com.chat.app.model.User;
import com.chat.app.model.UserBlock;
import com.chat.app.repository.UserBlockRepository;
import com.chat.app.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserBlockService {

    private final UserBlockRepository userBlockRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

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
        evictCache(userId, targetUserId);
        log.info("User {} blocked user {} successfully.", userId, targetUserId);
    }

    @Transactional
    public void unblockUser(UUID userId, UUID targetUserId) {
        UserBlock block = userBlockRepository.findByUserIdAndBlockedUserId(userId, targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("You have not blocked this user."));

        userBlockRepository.delete(block);
        evictCache(userId, targetUserId);
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

    @Transactional(readOnly = true)
    public List<UUID> getBlockedUserIds(UUID userId) {
        String key = "blocks:initiated:" + userId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                List<String> list = objectMapper.readValue(cached.toString(), new TypeReference<List<String>>() {});
                return list.stream().map(UUID::fromString).collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("Failed to deserialize blocked IDs for user: {}", userId, e);
            }
        }

        List<UUID> dbIds = userBlockRepository.findBlockedUserIds(userId);
        try {
            List<String> strList = dbIds.stream().map(UUID::toString).collect(Collectors.toList());
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(strList), Duration.ofHours(24));
        } catch (Exception e) {
            log.warn("Failed to cache blocked IDs for user: {}", userId, e);
        }
        return dbIds;
    }

    @Transactional(readOnly = true)
    public List<UUID> getUsersWhoBlockedMeIds(UUID userId) {
        String key = "blocks:targetOf:" + userId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                List<String> list = objectMapper.readValue(cached.toString(), new TypeReference<List<String>>() {});
                return list.stream().map(UUID::fromString).collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("Failed to deserialize who blocked me IDs for user: {}", userId, e);
            }
        }

        List<UUID> dbIds = userBlockRepository.findUsersWhoBlockedMe(userId);
        try {
            List<String> strList = dbIds.stream().map(UUID::toString).collect(Collectors.toList());
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(strList), Duration.ofHours(24));
        } catch (Exception e) {
            log.warn("Failed to cache who blocked me IDs for user: {}", userId, e);
        }
        return dbIds;
    }

    private void evictCache(UUID userId, UUID targetUserId) {
        try {
            redisTemplate.delete("blocks:initiated:" + userId);
            redisTemplate.delete("blocks:targetOf:" + targetUserId);
            // Evict reverse as well in case of bidirectional queries
            redisTemplate.delete("blocks:initiated:" + targetUserId);
            redisTemplate.delete("blocks:targetOf:" + userId);
        } catch (Exception e) {
            log.warn("Failed to evict block list cache for user {} or {}", userId, targetUserId, e);
        }
    }
}
