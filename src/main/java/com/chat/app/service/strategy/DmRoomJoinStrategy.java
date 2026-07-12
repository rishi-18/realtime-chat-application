package com.chat.app.service.strategy;

import com.chat.app.model.Room;
import com.chat.app.model.RoomType;
import com.chat.app.model.User;
import org.springframework.stereotype.Component;

@Component
public class DmRoomJoinStrategy implements RoomJoinStrategy {

    @Override
    public boolean supports(RoomType roomType) {
        return roomType == RoomType.DIRECT_MESSAGE;
    }

    @Override
    public void join(Room room, User user, String inviteCode) {
        throw new org.springframework.security.access.AccessDeniedException(
                "Direct message rooms cannot be joined directly."
        );
    }
}
