package com.chat.app.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PresenceServiceTest {

    private InMemoryPresenceService presenceService;
    private UUID userId;
    private String session1;
    private String session2;

    @BeforeEach
    void setUp() {
        presenceService = new InMemoryPresenceService();
        userId = UUID.randomUUID();
        session1 = "session-1";
        session2 = "session-2";
    }

    @Test
    void markOnline_And_IsUserOnline() {
        // Assert initial state
        assertFalse(presenceService.isUserOnline(userId));

        // Act - user connects first time
        presenceService.markOnline(userId, session1);

        // Assert
        assertTrue(presenceService.isUserOnline(userId));
    }

    @Test
    void markOffline_ClearsState_WhenLastSessionCloses() {
        // Arrange
        presenceService.markOnline(userId, session1);
        assertTrue(presenceService.isUserOnline(userId));

        // Act - last active session disconnects
        UUID offlineUserId = presenceService.markOffline(session1);

        // Assert
        assertEquals(userId, offlineUserId);
        assertFalse(presenceService.isUserOnline(userId));
    }

    @Test
    void markOffline_DoesNotClearState_WhenOtherSessionsActive() {
        // Arrange - multiple tabs / sessions
        presenceService.markOnline(userId, session1);
        presenceService.markOnline(userId, session2);
        assertTrue(presenceService.isUserOnline(userId));

        // Act - close one session
        UUID offlineUserId = presenceService.markOffline(session1);

        // Assert - user should still be online
        assertNull(offlineUserId);
        assertTrue(presenceService.isUserOnline(userId));

        // Act - close final session
        offlineUserId = presenceService.markOffline(session2);

        // Assert - user transitioned to offline
        assertEquals(userId, offlineUserId);
        assertFalse(presenceService.isUserOnline(userId));
    }

    @Test
    void getOnlineStatus_ReturnsCorrectMappings() {
        // Arrange
        UUID onlineUser = UUID.randomUUID();
        UUID offlineUser = UUID.randomUUID();
        presenceService.markOnline(onlineUser, session1);

        // Act
        Map<UUID, Boolean> result = presenceService.getOnlineStatus(Arrays.asList(onlineUser, offlineUser));

        // Assert
        assertTrue(result.get(onlineUser));
        assertFalse(result.get(offlineUser));
    }
}
