# Sequence Diagrams

This document contains sequence diagrams mapping client-server interactions for both REST and real-time WebSocket messaging lifecycles.

---

## 1. Create Room & Auto-Join (`POST /rooms`)

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Controller as RoomController
    participant Service as RoomService
    participant Repo as RoomRepository
    participant MemberRepo as RoomMemberRepository
    database DB as PostgreSQL

    Client->>Controller: POST /rooms (name, description)
    Note over Controller: Spring Security extracts UserPrincipal from JWT
    Controller->>Service: createRoom(RoomCreateRequest, UserPrincipal)
    Service->>Repo: existsByName(name)
    Repo-->>Service: false (available)
    Service->>Repo: save(Room)
    Repo->>DB: INSERT INTO rooms VALUES (...)
    DB-->>Repo: Saved Room
    Service->>MemberRepo: save(RoomMember)
    Note over MemberRepo: Enrolls creator into membership
    MemberRepo->>DB: INSERT INTO room_members VALUES (roomId, userId)
    DB-->>MemberRepo: Saved RoomMember
    Service-->>Controller: Room
    Controller-->>Client: 201 Created (RoomResponse)
```

---

## 2. WebSocket Connection Handshake & Authentication

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Gateway as HandshakeInterceptor
    participant Filter as ChannelInterceptor (STOMP)
    participant Provider as JwtTokenProvider
    participant Security as SecurityContextHolder

    Client->>Gateway: GET /ws (Upgrade: websocket)
    Gateway-->>Client: 101 Switching Protocols (Connection upgraded)
    Client->>Filter: Send CONNECT Frame with Header 'Authorization: Bearer <JWT>'
    Filter->>Provider: validateToken(JWT)
    Provider-->>Filter: true
    Filter->>Provider: getUserIdFromJWT(JWT)
    Provider-->>Filter: userId
    Filter->>Security: Set authentication context (UserPrincipal)
    Filter-->>Client: Send CONNECTED Frame
```

---

## 3. Real-Time Message Send & Broadcast Lifecycle

```mermaid
sequenceDiagram
    autonumber
    actor Client A
    participant Filter as ChannelInterceptor (STOMP)
    participant MemberRepo as RoomMemberRepository
    participant MsgController as MessageController
    participant MsgService as MessageService
    participant MsgRepo as MessageRepository
    participant Broker as SimpleBroker (In-Memory)
    actor Client B

    Client A->>Filter: Send SEND Frame to /app/chat.sendMessage (roomId, content)
    Filter->>MemberRepo: existsByRoomIdAndUserId(roomId, userId)
    alt user not member of room
        MemberRepo-->>Filter: false
        Filter-->>Client A: Block frame & return ERROR frame (Access Denied)
    end
    MemberRepo-->>Filter: true (authorized)
    Filter->>MsgController: Route payload
    MsgController->>MsgService: saveMessage(roomId, senderId, content)
    MsgService->>MsgRepo: save(Message)
    MsgRepo->>DB: INSERT INTO messages VALUES (...)
    DB-->>MsgRepo: Saved Message (timestamp, UUID)
    MsgRepo-->>MsgService: Message entity
    MsgService-->>MsgController: MessageResponse DTO
    MsgController->>Broker: publish(/topic/room.{roomId}, MessageResponse)
    Broker->>Client A: Broadcast message (STOMP MESSAGE frame)
    Broker->>Client B: Broadcast message (STOMP MESSAGE frame)
```
