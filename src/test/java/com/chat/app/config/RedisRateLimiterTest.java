package com.chat.app.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisRateLimiterTest {

    private RedisRateLimiter redisRateLimiter;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @BeforeEach
    void setUp() {
        redisRateLimiter = new RedisRateLimiter(redisTemplate);
    }

    @Test
    void tryConsume_AllowsFirstRequest() {
        String key = "test-key";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(key)).thenReturn(1L);

        boolean result = redisRateLimiter.tryConsume(key, 5, 60);

        assertTrue(result);
        verify(redisTemplate, times(1)).expire(eq(key), any(Duration.class));
    }

    @Test
    void tryConsume_AllowsWithinLimit() {
        String key = "test-key";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(key)).thenReturn(3L);

        boolean result = redisRateLimiter.tryConsume(key, 5, 60);

        assertTrue(result);
        verify(redisTemplate, never()).expire(eq(key), any(Duration.class));
    }

    @Test
    void tryConsume_RejectsExceedingLimit() {
        String key = "test-key";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(key)).thenReturn(6L);

        boolean result = redisRateLimiter.tryConsume(key, 5, 60);

        falseResultCheck(result);
        verify(redisTemplate, never()).expire(eq(key), any(Duration.class));
    }

    @Test
    void tryConsume_FallbackOnRedisOutage() {
        String key = "test-key";
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis connection error"));

        boolean result = redisRateLimiter.tryConsume(key, 5, 60);

        assertTrue(result); // Outage fallback returns true
    }

    private void falseResultCheck(boolean result) {
        assertFalse(result);
    }
}
