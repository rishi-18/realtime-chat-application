package com.chat.app.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.test.context.TestConfiguration;
import redis.embedded.RedisServer;

@TestConfiguration
public class TestRedisConfiguration {

    private RedisServer redisServer;

    @PostConstruct
    public void startRedis() {
        try {
            redisServer = RedisServer.builder()
                    .port(6379)
                    .setting("maxheap 64mb")
                    .build();
            redisServer.start();
            System.out.println("Successfully started embedded Redis on port 6379 with maxheap 64mb.");
        } catch (Exception e) {
            System.err.println("Could not start embedded Redis (it might already be running): " + e.getMessage());
        }
    }

    @PreDestroy
    public void stopRedis() {
        if (redisServer != null) {
            try {
                redisServer.stop();
                System.out.println("Successfully stopped embedded Redis.");
            } catch (Exception e) {
                System.err.println("Could not stop embedded Redis: " + e.getMessage());
            }
        }
    }
}
