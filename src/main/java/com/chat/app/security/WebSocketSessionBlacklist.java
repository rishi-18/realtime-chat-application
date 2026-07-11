package com.chat.app.security;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionBlacklist {

    private final Set<UUID> blacklistedUserIds = ConcurrentHashMap.newKeySet();

    public void blacklistUser(UUID userId) {
        if (userId != null) {
            blacklistedUserIds.add(userId);
        }
    }

    public void removeUserFromBlacklist(UUID userId) {
        if (userId != null) {
            blacklistedUserIds.remove(userId);
        }
    }

    public boolean isUserBlacklisted(UUID userId) {
        return userId != null && blacklistedUserIds.contains(userId);
    }

    public void clearBlacklist() {
        blacklistedUserIds.clear();
    }
}
