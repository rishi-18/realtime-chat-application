package com.chat.app.service.strategy;

import com.chat.app.model.Room;
import com.chat.app.model.RoomMember;
import com.chat.app.model.RoomMemberId;
import com.chat.app.model.RoomRole;
import com.chat.app.model.RoomType;
import com.chat.app.model.User;
import com.chat.app.repository.RoomMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class PublicRoomJoinStrategy implements RoomJoinStrategy {

    private final RoomMemberRepository roomMemberRepository;

    @Override
    public boolean supports(RoomType roomType) {
        return roomType == RoomType.PUBLIC_GROUP;
    }

    @Override
    public void join(Room room, User user, String inviteCode) {
        if (roomMemberRepository.existsByIdRoomIdAndIdUserId(room.getId(), user.getId())) {
            throw new IllegalArgumentException("You are already a member of this room.");
        }

        RoomMemberId membershipId = RoomMemberId.builder()
                .roomId(room.getId())
                .userId(user.getId())
                .build();

        RoomMember membership = RoomMember.builder()
                .id(membershipId)
                .room(room)
                .user(user)
                .role(RoomRole.MEMBER)
                .joinedAt(Instant.now())
                .build();

        roomMemberRepository.save(membership);
    }
}
