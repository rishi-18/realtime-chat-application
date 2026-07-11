# System Architecture

This document outlines the overall architecture of the Real-Time Chat Platform, detailing components, service boundaries, and interactions.

---

## 1. System Overview

The platform uses a layered monolithic architecture built on Spring Boot, designed to easily decompose into isolated microservices.

```mermaid
graph TD
    Client[Client Apps] -->|HTTPS / REST| LB[Load Balancer / Proxy]
    Client -->|TCP / WebSockets| LB
    
    LB -->|Route REST| RESTGateway[Spring Security Filter Chain]
    LB -->|Route WS| WSGateway[WebSocket STOMP Broker]
    
    subgraph Spring Boot Chat Server
        RESTGateway --> AuthController[AuthController]
        RESTGateway --> RoomController[RoomController]
        
        WSGateway --> Handshake[Handshake Interceptor]
        Handshake --> SecurityInterceptor[STOMP ChannelInterceptor]
        SecurityInterceptor --> MessageController[MessageController]
        
        AuthController --> AuthService[AuthService]
        RoomController --> RoomService[RoomService]
        MessageController --> MessageService[MessageService]
        
        AuthService --> Repos[JPA Repositories]
        RoomService --> Repos
        MessageService --> Repos
    end
    
    Repos -->|SQL| DB[(PostgreSQL Database)]
    MessageService -->|Publish / Broadcast| SimpleBroker[Spring SimpleBroker]
    SimpleBroker --> Client
```

---

## 2. Component Responsibilities

| Component | Responsibility |
| :--- | :--- |
| **Client Applications** | Handles user interface, saves JWT access tokens in memory, and negotiates STOMP connections. |
| **Load Balancer** | Handles HTTPS/TLS termination and routes HTTP REST or WebSocket TCP traffic to application nodes. |
| **Spring Security Filter Chain** | Authenticates incoming REST requests stateless using the Authorization header JWT. |
| **STOMP ChannelInterceptor** | Intercepts incoming WebSocket/STOMP packets. Performs authentication on `CONNECT` and verifies channel access on `SUBSCRIBE`/`SEND`. |
| **REST Controllers** | Deserializes payloads, validates schemas (JSR-380), and returns REST responses. |
| **WebSocket Controllers** | Receives real-time incoming messages, coordinates database persistence, and relays to the broker. |
| **Domain Services** | Coordinates transaction boundaries, executes business logic, and encodes credentials. |
| **Presence Service** | Manages online/offline user states in memory and coordinates real-time status broadcasts. |
| **PostgreSQL Database** | Persistent storage for user records, room details, membership lists, and conversation logs. |

---

## 3. Service Interactions

### 1. Registration & Authentication (REST)
- User signs up/in via `AuthController`. If successful, the server issues a database-backed Refresh Token and a JWT.

### 2. Room Configuration (REST)
- Users call `RoomController` to create rooms, join rooms, or initiate direct messages (DMs) with other users. These write operations are transactionally persisted to SQL. DMs are registered as a distinct room type.

### 3. Real-Time Message Exchange (WebSockets + STOMP)
- Clients upgrade their connection to raw WebSockets on endpoint `/ws`.
- The connection is validated by checking the JWT in the STOMP header.
- When sending a message to `/app/chat.sendMessage`, the server persists the message payload to the database asynchronously or synchronously, then publishes the message to the corresponding `/topic/room.{roomId}` broker channel, notifying all active subscribers.

### 4. User Presence Tracking (Spring Events + STOMP Broadcast)
- When a user establishes a STOMP connection, Spring publishes a `SessionConnectEvent`.
- A presence listener catches this event, registers the user as online in `PresenceService`, and broadcasts a presence status event (`ONLINE`) to `/topic/presence.{roomId}` for all rooms the user belongs to.
- When the socket connection terminates, a `SessionDisconnectEvent` fires, marking the user as `OFFLINE` and broadcasting the status change.

### 5. Real-Time Typing Indicators (Transient WebSocket Event)
- When a user starts typing, the client publishes a STOMP frame to `/app/chat.typing`.
- The interceptor verifies room membership to block unauthorized alerts.
- The server bypasses database persistence entirely, immediately routing the typing notification payload as a broadcast to `/topic/typing.{roomId}`.
- All active room subscribers receive the payload and render the indicator.

### 6. Message Read Receipts (WebSocket Event + SQL persistence)
- When a user reads messages in a room, the client publishes a STOMP frame to `/app/chat.readReceipt` indicating their last read message ID.
- The interceptor verifies room membership.
- The server updates `last_read_message_id` on the user's `RoomMember` record in PostgreSQL.
- The server broadcasts the receipt update to `/topic/receipts.{roomId}` to inform other members of their updated read pointer.

### 7. Media & Attachment Handling (REST Upload + WebSocket Alert)
- Users upload media files (images, files) via `MediaController` using multipart forms.
- The server validates file types and sizes.
- The file is saved to the local file system (or object storage).
- The server returns the media URL and metadata.
- When the client sends the message via WebSocket, it includes the media URL, linking the attachment to the chat log.

### 8. Message Editing & Deletion (REST Trigger + WebSocket Sync)
- A user triggers a message update or deletion via REST endpoints (`PUT/DELETE /api/v1/messages/{messageId}`).
- The server validates ownership of the target message (caller must be the original sender).
- On edit: content is modified, and the `updatedAt` field is set.
- On delete: the message is soft-deleted by setting the `content = null` and raising an `is_deleted` flag, preserving relational receipts.
- The server broadcasts the update payload to `/topic/room.{roomId}`. Peer clients receive this frame and update their local message UI state in real-time.

### 9. Message Reactions (REST Toggle + WebSocket Sync)
- A user adds or removes a reaction (emoji) on a message via REST endpoint `POST /api/v1/messages/{messageId}/reactions`.
- The server validates that the user is a member of the room where the target message resides.
- The server adds/deletes a record in the `message_reactions` table using a toggle mechanism.
- The server issues a STOMP broadcast to `/topic/room.{roomId}` containing the message ID and updated reaction count details. Peer clients sync their UI reaction indicators in real-time.

### 10. Message Search (REST Full-Text Search)
- A user submits a keyword search request to the REST endpoint `GET /api/v1/rooms/{roomId}/messages/search?query=keyword`.
- The server validates that the user is a member of the target room.
- The server queries the database using full-text search operators against the `content` field.
- Database index scans are executed via a GIN index on message content vector representations.
- The server returns a paginated list of matching message DTO responses.

### 11. User Mentions (@Mentions & Private Notification Syncing)
- When a message is sent, the server parses the content for `@username` patterns.
- For each detected username, the server verifies that the user exists and is a member of the room.
- Verified mentions are persisted in the `message_mentions` table.
- The server broadcasts private real-time notification alerts to each mentioned user via their secure user-specific queues `/user/queue/notifications`. Peer clients intercept this frame to trigger push updates or audio pings.

### 12. Message Pins (REST Toggle + WebSocket Sync)
- A user pins or unpins a message in a room via REST endpoint `POST /api/v1/messages/{messageId}/pin`.
- The server validates room membership, target message existence, and that the message has not been soft-deleted.
- Pin state changes are persisted in the `pinned_messages` table, housing pin metadata (who pinned it and when).
- The server issues a STOMP broadcast to `/topic/room.{roomId}` syncing the pin state change across all room members' screens.
- Room members can query all active pinned messages in a room via `GET /api/v1/rooms/{roomId}/pins` (fetching only from the `pinned_messages` join table for sub-millisecond retrieval).
