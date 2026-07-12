package com.chat.app.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisRateLimiter {

    private final RedisTemplate<String, Object> redisTemplate;

    public boolean tryConsume(String key, int limit, int windowSeconds) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
            }
            return count != null && count <= limit;
        } catch (Exception e) {
            log.error("Redis rate limiter lookup failed for key: {}. Falling back to allow access.", key, e);
            return true; // Fallback to allow access to prevent outage
        }
    }
}
