package com.chat.app.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PresenceService {
    /**
     * Mark a user session as online.
     * Handles incrementing session counts for multi-connection scenarios.
     *
     * @param userId    the UUID of the user
     * @param sessionId the WebSocket session identifier
     */
    void markOnline(UUID userId, String sessionId);

    /**
     * Mark a WebSocket session as offline.
     * Returns the UUID of the user if this connection termination caused
     * the user to transition fully to offline state (session count = 0).
     *
     * @param sessionId the WebSocket session identifier
     * @return the UUID of the user if transitioned offline, otherwise null
     */
    UUID markOffline(String sessionId);

    /**
     * Check if a specific user is currently online.
     *
     * @param userId the UUID of the user
     * @return true if the user is online, false otherwise
     */
    boolean isUserOnline(UUID userId);

    /**
     * Retrieve the online status for a list of user IDs.
     *
     * @param userIds the list of user UUIDs
     * @return a map of user UUID to their online status (true = online)
     */
    Map<UUID, Boolean> getOnlineStatus(List<UUID> userIds);
}
