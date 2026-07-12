package com.chat.app.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
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
        Refill refill = Refill.intervally(100, Duration.ofMinutes(1));
        Bandwidth limit = Bandwidth.classic(100, refill);
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    private Bucket createNewWsBucket() {
        // Limit: 30 messages per minute (anti-spam)
        Refill refill = Refill.intervally(30, Duration.ofMinutes(1));
        Bandwidth limit = Bandwidth.classic(30, refill);
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
