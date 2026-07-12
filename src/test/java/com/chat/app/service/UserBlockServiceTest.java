package com.chat.app.service;

import com.chat.app.dto.UserResponse;
import com.chat.app.model.User;
import com.chat.app.model.UserBlock;
import com.chat.app.repository.UserBlockRepository;
import com.chat.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserBlockServiceTest {

    private UserBlockService userBlockService;

    @Mock
    private UserBlockRepository userBlockRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    @Mock
    private org.springframework.data.redis.core.ValueOperations<String, Object> valueOperations;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private User user;
    private User target;

    @BeforeEach
    void setUp() {
        userBlockService = new UserBlockService(userBlockRepository, userRepository, redisTemplate, objectMapper);
        user = User.builder().id(UUID.randomUUID()).username("user").email("user@example.com").build();
        target = User.builder().id(UUID.randomUUID()).username("target").email("target@example.com").build();
    }

    @Test
    void blockUser_Success() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userBlockRepository.existsByUserIdAndBlockedUserId(user.getId(), target.getId())).thenReturn(false);

        assertDoesNotThrow(() -> userBlockService.blockUser(user.getId(), target.getId()));

        verify(userBlockRepository, times(1)).save(any(UserBlock.class));
    }

    @Test
    void blockUser_ThrowsException_WhenSelfBlocking() {
        assertThrows(IllegalArgumentException.class, () -> userBlockService.blockUser(user.getId(), user.getId()));
    }

    @Test
    void unblockUser_Success() {
        UserBlock block = UserBlock.builder().id(UUID.randomUUID()).user(user).blockedUser(target).build();
        when(userBlockRepository.findByUserIdAndBlockedUserId(user.getId(), target.getId())).thenReturn(Optional.of(block));

        assertDoesNotThrow(() -> userBlockService.unblockUser(user.getId(), target.getId()));

        verify(userBlockRepository, times(1)).delete(block);
    }

    @Test
    void getBlockedUsers_ReturnsMappedList() {
        UserBlock block = UserBlock.builder().id(UUID.randomUUID()).user(user).blockedUser(target).build();
        when(userBlockRepository.findByUserId(user.getId())).thenReturn(Collections.singletonList(block));

        List<UserResponse> responses = userBlockService.getBlockedUsers(user.getId());

        assertEquals(1, responses.size());
        assertEquals(target.getUsername(), responses.get(0).getUsername());
    }

    @Test
    void isBlockedSymmetrically_ReturnsTrue_IfUserBlocked() {
        when(userBlockRepository.existsByUserIdAndBlockedUserId(user.getId(), target.getId())).thenReturn(true);

        assertTrue(userBlockService.isBlockedSymmetrically(user.getId(), target.getId()));
    }

    @Test
    void getBlockedUserIds_CacheHit() throws Exception {
        String key = "blocks:initiated:" + user.getId();
        String cachedJson = "[\"" + target.getId() + "\"]";
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn(cachedJson);
        when(objectMapper.readValue(eq(cachedJson), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(Collections.singletonList(target.getId().toString()));

        List<UUID> result = userBlockService.getBlockedUserIds(user.getId());

        assertEquals(1, result.size());
        assertEquals(target.getId(), result.get(0));
        verify(userBlockRepository, never()).findBlockedUserIds(any());
    }

    @Test
    void getBlockedUserIds_CacheMiss() throws Exception {
        String key = "blocks:initiated:" + user.getId();
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn(null);
        when(userBlockRepository.findBlockedUserIds(user.getId())).thenReturn(Collections.singletonList(target.getId()));

        List<UUID> result = userBlockService.getBlockedUserIds(user.getId());

        assertEquals(1, result.size());
        assertEquals(target.getId(), result.get(0));
        verify(userBlockRepository, times(1)).findBlockedUserIds(user.getId());
    }
}
