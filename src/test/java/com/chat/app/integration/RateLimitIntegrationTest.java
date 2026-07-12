package com.chat.app.integration;

import com.chat.app.config.TestRedisConfiguration;
import com.chat.app.config.RedisRateLimiter;
import com.chat.app.dto.LoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfiguration.class)
@AutoConfigureMockMvc
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisRateLimiter redisRateLimiter;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        redisTemplate.delete("ratelimit:http:ip:127.0.0.1");
    }

    @Test
    void rateLimiter_EnforcesLimit_Returns429() throws Exception {
        String key = "ratelimit:http:ip:127.0.0.1";

        // Consume 100 requests to hit the limit
        for (int i = 0; i < 100; i++) {
            boolean success = redisRateLimiter.tryConsume(key, 100, 60);
            assertTrue(success, "Request " + (i + 1) + " should be allowed");
        }

        // The 101st direct check should be blocked
        boolean block = redisRateLimiter.tryConsume(key, 100, 60);
        assertFalse(block, "The 101st request should be blocked");

        LoginRequest loginRequest = LoginRequest.builder()
                .usernameOrEmail("someone")
                .password("anypassword")
                .build();

        // Perform a request - this will call the filter, see the rate limit is exceeded, and trigger 429
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest))
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().is(HttpStatus.TOO_MANY_REQUESTS.value()))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("Rate limit exceeded. Please try again later."));
    }
}
