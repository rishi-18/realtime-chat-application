package com.chat.app.config;

import io.github.bucket4j.Bucket;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class RateLimitConfig {

    private final Map<String, Bucket> httpBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> wsBuckets = new ConcurrentHashMap<>();

    public Bucket resolveHttpBucket(String key) {
        return httpBuckets.computeIfAbsent(key, k -> createNewHttpBucket());
    }

    public Bucket resolveWsBucket(String key) {
        return wsBuckets.computeIfAbsent(key, k -> createNewWsBucket());
    }

    private Bucket createNewHttpBucket() {
        // Limit: 100 requests per minute
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(100).refillIntervally(100, Duration.ofMinutes(1)))
                .build();
    }

    private Bucket createNewWsBucket() {
        // Limit: 30 messages per minute (anti-spam)
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(30).refillIntervally(30, Duration.ofMinutes(1)))
                .build();
    }
}
