package com.chat.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitingFilterTest {

    private RateLimitingFilter rateLimitingFilter;

    @Mock
    private RedisRateLimiter redisRateLimiter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        rateLimitingFilter = new RateLimitingFilter(redisRateLimiter, objectMapper);
    }

    @Test
    void doFilterInternal_AllowsRequest_WhenTokensAvailable() throws Exception {
        // Arrange
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(redisRateLimiter.tryConsume("ratelimit:http:ip:127.0.0.1", 100, 60)).thenReturn(true);

        // Act
        rateLimitingFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain, times(1)).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void doFilterInternal_RejectsRequest_WhenRateLimitExceeded() throws Exception {
        // Arrange
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(redisRateLimiter.tryConsume("ratelimit:http:ip:127.0.0.1", 100, 60)).thenReturn(false);

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);

        // Act
        rateLimitingFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain, never()).doFilter(request, response);
        verify(response, times(1)).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        assertTrue(stringWriter.toString().contains("RATE_LIMIT_EXCEEDED"));
    }
}
