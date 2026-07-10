# Data Flow

This document details how data moves through the Chat Platform for both REST APIs and WebSocket STOMP messaging channels.

---

## 1. REST Data Flow (Channel Creation)

```text
[Client Request]
       │ (POST /api/v1/rooms with headers: Authorization Bearer JWT)
       ▼
[JwtAuthenticationFilter]
       │ (Validates JWT signature, populates SecurityContext with UserPrincipal)
       ▼
[RoomController.createRoom()]
       │ (Validates input parameters using JSR-380 validation)
       ▼
[RoomService.createRoom()]
       ├─── Step 1: UserRepository.findById() (Get owner User entity)
       ├─── Step 2: RoomRepository.existsByName() (Check unique constraint)
       ├─── Step 3: RoomRepository.save() (Saves Room record)
       └─── Step 4: RoomMemberRepository.save() (Binds creator to room membership)
       ▼
[PostgreSQL Database]
       │ (Persists transaction logs to rooms and room_members tables)
       ▼
[Client Response]
       (JSON: roomId, name, description, createdBy, createdAt - Status 201)
```

---

## 2. WebSocket Connection and Handshake Flow

```text
[Client Upgrade Request]
       │ (GET ws://localhost:8080/ws with WebSocket headers)
       ▼
[HandshakeInterceptor]
       │ (Checks upgrade parameters, passes validation)
       ▼
[STOMP CONNECT Frame]
       │ (Contains STOMP Header 'Authorization: Bearer <JWT>')
       ▼
[ChannelInterceptor.preSend()]
       ├─── Step 1: Inspect frame command -> CONNECT
       ├─── Step 2: Extract Bearer JWT from headers
       ├─── Step 3: Validate JWT cryptographic signature (JwtTokenProvider)
       ├─── Step 4: Extract User UUID, load UserDetails
       └─── Step 5: Inject Authentication object into STOMP message headers
       ▼
[Client Connected Response]
       (STOMP CONNECTED Frame returned to client)
```

---

## 3. Real-Time Message Broadcast Flow

```text
[Client SEND Frame]
       │ (Destination: /app/chat.sendMessage; Payload: roomId, content)
       ▼
[ChannelInterceptor.preSend()]
       ├─── Step 1: Inspect frame command -> SEND
       ├─── Step 2: Extract User principal from header authentication context
       ├─── Step 3: Validate membership: RoomMemberRepository.existsByRoomIdAndUserId()
       └─── Step 4: If not a member, block frame and raise AccessDeniedException
       ▼
[MessageController.handleMessage()]
       │ (Invoked by Spring Message routing engine)
       ▼
[MessageService.saveMessage()]
       ├─── Step 1: Fetch Room and User entities
       ├─── Step 2: Map to Message entity
       └─── Step 3: MessageRepository.save() (Synchronous/Async commit to SQL)
       ▼
[Spring MessageBroker (SimpleBroker)]
       │ (Maps roomId to active WS session subscription list)
       ▼
[Outbound Message Channel]
       │ (Dispatches raw JSON message payloads to all online subscribers)
       ▼
[Clients Subscribers]
       (Receive STOMP MESSAGE frames - Destination: /topic/room.{roomId})
```

---

## 4. User Presence Tracking and Broadcast Flow

```text
[WebSocket Session Event]
       │ (Spring publishes SessionConnectEvent or SessionDisconnectEvent)
       ▼
[PresenceEventListener]
       ├─── Step 1: Detect Connection/Disconnection state
       ├─── Step 2: Extract User principal details (UserPrincipal)
       ├─── Step 3: Call PresenceService.markOnline() / markOffline()
       ├─── Step 4: Fetch user's active memberships: RoomMemberRepository.findByUserId()
       ▼
[Spring MessageBroker (SimpleBroker)]
       │ (Loop through rooms and broadcast PresenceEvent to "/topic/presence.{roomId}")
       ▼
[Outbound Message Channel]
       │ (Dispatches raw JSON presence payload to channel subscribers)
       ▼
[Connected Clients]
       (Receive STOMP MESSAGE frames - Destination: /topic/presence.{roomId})
```

---

## 5. Transient Typing Alerts Flow

```text
[Client SEND Frame]
       │ (Destination: /app/chat.typing; Payload: roomId, isTyping)
       ▼
[ChannelInterceptor.preSend()]
       ├─── Step 1: Verify user principal context
       ├─── Step 2: Validate membership: existsByIdRoomIdAndIdUserId(roomId, userId)
       └─── Step 3: Block unauthorized frames
       ▼
[MessageController.handleTyping()]
       │ (Invoked by Spring Message routing engine)
       ▼
[Spring MessageBroker (SimpleBroker)]
       │ (Instantly routes payload to /topic/typing.{roomId})
       ▼
[Outbound Message Channel]
       │ (Dispatches raw JSON typing payloads - Bypasses Database entirely)
       ▼
[Room Subscribers]
       (Receive STOMP MESSAGE frames - Destination: /topic/typing.{roomId})
```
