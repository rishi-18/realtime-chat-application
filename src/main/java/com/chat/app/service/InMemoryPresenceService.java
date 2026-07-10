package com.chat.app.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryPresenceService implements PresenceService {

    // Maps sessionId -> userId
    private final Map<String, UUID> sessionUserMap = new ConcurrentHashMap<>();

    // Maps userId -> count of active sessions
    private final Map<UUID, Integer> userSessionCountMap = new ConcurrentHashMap<>();

    @Override
    public void markOnline(UUID userId, String sessionId) {
        if (userId == null || sessionId == null) return;

        sessionUserMap.put(sessionId, userId);

        userSessionCountMap.compute(userId, (key, currentCount) -> {
            if (currentCount == null) {
                return 1;
            } else {
                return currentCount + 1;
            }
        });
    }

    @Override
    public UUID markOffline(String sessionId) {
        if (sessionId == null) return null;

        UUID userId = sessionUserMap.remove(sessionId);
        if (userId == null) return null;

        final boolean[] transitionedOffline = {false};

        userSessionCountMap.compute(userId, (key, currentCount) -> {
            if (currentCount == null || currentCount <= 1) {
                transitionedOffline[0] = true;
                return null; // Removes key from map, marking user offline
            } else {
                return currentCount - 1;
            }
        });

        return transitionedOffline[0] ? userId : null;
    }

    @Override
    public boolean isUserOnline(UUID userId) {
        if (userId == null) return false;
        return userSessionCountMap.containsKey(userId);
    }

    @Override
    public Map<UUID, Boolean> getOnlineStatus(List<UUID> userIds) {
        Map<UUID, Boolean> statusMap = new HashMap<>();
        if (userIds == null) return statusMap;

        for (UUID id : userIds) {
            statusMap.put(id, isUserOnline(id));
        }
        return statusMap;
    }
}
