package com.chat.app.service;

import com.chat.app.dto.*;
import com.chat.app.exception.TokenRefreshException;
import com.chat.app.model.RefreshToken;
import com.chat.app.model.User;
import com.chat.app.security.JwtTokenProvider;
import com.chat.app.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private com.chat.app.security.JwtProperties jwtProperties;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        lenient().when(jwtProperties.getAccessTokenExpirationMs()).thenReturn(900000L);
    }

    @Test
    void authenticateUser_Success() {
        // Arrange
        LoginRequest loginRequest = new LoginRequest("testuser", "Password123!");
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(userId, "testuser", "testuser@example.com", "hash", Collections.emptyList());
        
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        
        when(tokenProvider.generateToken(principal)).thenReturn("access_token_jwt");
        
        RefreshToken refreshToken = RefreshToken.builder()
                .token("refresh_token_uuid")
                .build();
        when(refreshTokenService.createRefreshToken(userId)).thenReturn(refreshToken);

        // Act
        JwtAuthenticationResponse response = authService.authenticateUser(loginRequest);

        // Assert
        assertNotNull(response);
        assertEquals("access_token_jwt", response.getAccessToken());
        assertEquals("refresh_token_uuid", response.getRefreshToken());
        assertEquals(900L, response.getExpiresIn());
    }

    @Test
    void refreshAccessToken_Success() {
        // Arrange
        TokenRefreshRequest refreshRequest = new TokenRefreshRequest("refresh_token_uuid");
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("testuser@example.com")
                .passwordHash("hash")
                .build();
        
        RefreshToken refreshToken = RefreshToken.builder()
                .token("refresh_token_uuid")
                .user(user)
                .expiryDate(Instant.now().plusSeconds(3600))
                .build();

        when(refreshTokenService.findByToken("refresh_token_uuid")).thenReturn(Optional.of(refreshToken));
        when(refreshTokenService.verifyExpiration(refreshToken)).thenReturn(refreshToken);
        when(tokenProvider.generateToken(any(UserPrincipal.class))).thenReturn("new_access_token");

        // Act
        TokenRefreshResponse response = authService.refreshAccessToken(refreshRequest);

        // Assert
        assertNotNull(response);
        assertEquals("new_access_token", response.getAccessToken());
        assertEquals(900L, response.getExpiresIn());
    }

    @Test
    void refreshAccessToken_ThrowsTokenRefreshException_WhenTokenNotFound() {
        // Arrange
        TokenRefreshRequest refreshRequest = new TokenRefreshRequest("missing_token");
        when(refreshTokenService.findByToken("missing_token")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(TokenRefreshException.class, () -> authService.refreshAccessToken(refreshRequest));
        verify(tokenProvider, never()).generateToken(any(UserPrincipal.class));
    }
}
