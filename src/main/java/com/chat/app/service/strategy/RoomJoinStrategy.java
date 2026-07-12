package com.chat.app.service.strategy;

import com.chat.app.model.Room;
import com.chat.app.model.RoomType;
import com.chat.app.model.User;

public interface RoomJoinStrategy {
    boolean supports(RoomType roomType);
    void join(Room room, User user, String inviteCode);
}
