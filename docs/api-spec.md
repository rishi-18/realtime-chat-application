# API Specification

This document defines the REST API endpoints and WebSocket events for the Chat Platform.

---

## Global API Standards

- **Base URL**: `/api/v1`
- **Content-Type**: `application/json`
- **REST Authentication**: Bearer Token via `Authorization: Bearer <JWT>`
- **WebSocket STOMP Endpoint**: `ws://localhost:8080/ws`

---

## 1. REST Endpoints (Channel Management)

### Create Room
Creates a new chat channel. The creator is joined to the room automatically.
*   **Route**: `POST /rooms`
*   **Authentication**: Required (Valid Access Token)
*   **Request Body**:
    - `name` (string, required): 3-50 chars, alphanumeric, hyphens, and underscores.
    - `description` (string, optional): Max 255.
    ```json
    {
      "name": "developer-room",
      "description": "Tech discussions"
    }
    ```
*   **Success Response (`201 Created`)**:
    ```json
    {
      "id": "e3b0c442-9c0b-4ef8-bb6d-6bb9bd380a11",
      "name": "developer-room",
      "description": "Tech discussions",
      "createdBy": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
      "createdAt": "2026-07-11T03:45:00Z",
      "roomType": "PUBLIC_GROUP"
    }
    ```
*   **Errors**:
    - `400 Bad Request`: `VALIDATION_ERROR` (Invalid channel name structure).
    - `400 Bad Request`: `ROOM_ALREADY_EXISTS` (Channel name already in use).

### Create or Retrieve Direct Message (DM) Room
Initiates a 1-to-1 private chat channel with another user, or returns the existing DM room if one already exists.
*   **Route**: `POST /rooms/dm`
*   **Authentication**: Required (Valid Access Token)
*   **Request Body**:
    - `recipientId` (string, required): The UUID of the recipient user.
    ```json
    {
      "recipientId": "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b22"
    }
    ```
*   **Success Response (`200 OK` or `201 Created`)**:
    ```json
    {
      "id": "e3b0c442-9c0b-4ef8-bb6d-6bb9bd380a11",
      "name": "dm:a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11:b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b22",
      "description": "Direct message room",
      "createdBy": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
      "createdAt": "2026-07-11T04:15:00Z",
      "roomType": "DIRECT_MESSAGE"
    }
    ```
*   **Errors**:
    - `400 Bad Request`: `USER_NOT_FOUND` (Recipient user not found).
    - `400 Bad Request`: `SELF_DM_FORBIDDEN` (Cannot start a direct message with yourself).

### List Rooms
Lists all available rooms on the platform.
*   **Route**: `GET /rooms`
*   **Authentication**: Required (Valid Access Token)
*   **Success Response (`200 OK`)**:
    ```json
    [
      {
        "id": "e3b0c442-9c0b-4ef8-bb6d-6bb9bd380a11",
        "name": "developer-room",
        "description": "Tech discussions",
        "createdBy": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
        "createdAt": "2026-07-11T03:45:00Z",
        "roomType": "PUBLIC_GROUP"
      }
    ]
    ```

### Join Room
Registers a user as a member of a room. Required before subscribing or sending messages.
*   **Route**: `POST /rooms/{roomId}/join`
*   **Authentication**: Required (Valid Access Token)
*   **Success Response (`200 OK`)**:
    ```json
    {
      "success": true,
      "message": "Joined room successfully."
    }
    ```
*   **Errors**:
    - `404 Not Found`: `ROOM_NOT_FOUND` (Target room does not exist).
    - `400 Bad Request`: `ALREADY_MEMBER` (User is already a member of this room).

### Upload Media Attachment
Uploads a file or image to be attached to a message.
*   **Route**: `POST /media/upload`
*   **Authentication**: Required (Valid Access Token)
*   **Request Format**: `multipart/form-data`
    - `file` (MultipartFile, required): Max size 10MB.
*   **Success Response (`200 OK`)**:
    ```json
    {
      "fileName": "attachment.png",
      "fileUrl": "/uploads/attachment-a0eebc99-9c0b.png",
      "fileType": "image/png",
      "fileSize": 1048576
    }
    ```
*   **Errors**:
    - `400 Bad Request`: `BAD_REQUEST` (File size exceeds limit or unsupported file type).

### Edit Message
Modifies the text content of a message. Only the sender can perform this action.
*   **Route**: `PUT /messages/{messageId}`
*   **Authentication**: Required (Valid Access Token)
*   **Request Payload**:
    ```json
    {
      "content": "Updated chat content"
    }
    ```
*   **Success Response (`200 OK`)**: MessageResponse containing updated fields, with `isEdited` flag set.
*   **Errors**:
    - `403 Forbidden`: `ACCESS_DENIED` (User is not the sender of the message).
    - `404 Not Found`: `MESSAGE_NOT_FOUND` (Message does not exist).

### Delete Message
Soft-deletes a message. Content is removed, attachments are detached, and the `isDeleted` flag is raised. Only the sender can perform this action.
*   **Route**: `DELETE /messages/{messageId}`
*   **Authentication**: Required (Valid Access Token)
*   **Success Response (`200 OK`)**:
    ```json
    {
      "success": true,
      "message": "Message deleted successfully."
    }
    ```
*   **Errors**:
    - `403 Forbidden`: `ACCESS_DENIED` (User is not the sender of the message).
    - `404 Not Found`: `MESSAGE_NOT_FOUND` (Message does not exist).

### Toggle Message Reaction
Toggles (adds or removes) an emoji reaction on a message. The user must be a member of the room where the message is located.
*   **Route**: `POST /messages/{messageId}/reactions`
*   **Authentication**: Required (Valid Access Token)
*   **Request Payload**:
    ```json
    {
      "emoji": "🚀"
    }
    ```
*   **Success Response (`200 OK`)**:
    ```json
    {
      "messageId": "76161474-9c0b-4ef8-bb6d-6bb9bd380a11",
      "emoji": "🚀",
      "action": "ADDED", // or "REMOVED"
      "userId": "d2b2b4bc-a1cd-44b5-9624-383a440ab8d2",
      "username": "testuser"
    }
    ```
*   **Errors**:
    - `403 Forbidden`: `ACCESS_DENIED` (User is not a member of the room).
    - `404 Not Found`: `MESSAGE_NOT_FOUND` (Message does not exist).
    - `400 Bad Request`: `BAD_REQUEST` (Invalid emoji character payload).

### Fetch Message History (Paginated)
Retrieves historical messages for a room, ordered by creation date descending.
*   **Route**: `GET /rooms/{roomId}/messages`
*   **Query Parameters**:
    - `page` (number, optional, default: 0): Page index.
    - `size` (number, optional, default: 50): Number of messages per page.
*   **Authentication**: Required (Valid Access Token, User must be a room member)
*   **Success Response (`200 OK`)**:
    ```json
    {
      "content": [
        {
          "id": "76161474-9c0b-4ef8-bb6d-6bb9bd380a11",
          "senderId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
          "senderUsername": "testuser",
          "content": "Hello World!",
          "timestamp": "2026-07-11T03:50:00Z"
        }
      ],
      "pageable": {
        "pageNumber": 0,
        "pageSize": 50
      },
      "last": true
    }
    ```

### Fetch Room Presence
Retrieves the online status of all members of a chat room.
*   **Route**: `GET /rooms/{roomId}/presence`
*   **Authentication**: Required (Valid Access Token, User must be a room member)
*   **Success Response (`200 OK`)**:
    ```json
    [
      {
        "userId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
        "username": "testuser",
        "status": "ONLINE"
      },
      {
        "userId": "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b22",
        "username": "anotheruser",
        "status": "OFFLINE"
      }
    ]
    ```

---

## 2. WebSocket & STOMP Protocol Events

### 1. Connection Handshake
*   **URL**: `ws://localhost:8080/ws`
*   **Protocol**: STOMP 1.1 or 1.2
*   **Handshake Request Headers**:
    ```text
    CONNECT
    accept-version:1.1,1.2
    Authorization: Bearer <JWT>
    ```

### 2. Topic Subscription
To listen to real-time broadcasts, clients must subscribe to the room topic:
*   **Destination**: `/topic/room.{roomId}`
*   **Authorization**: Filter checks if the client username is registered in the `room_members` database table. If not, subscription is blocked.

### 3. Messaging Sending
Clients send messages to the processing prefix:
*   **Destination**: `/app/chat.sendMessage`
*   **Payload**:
    ```json
    {
      "roomId": "e3b0c442-9c0b-4ef8-bb6d-6bb9bd380a11",
      "content": "Hello world!"
    }
    ```
*   **Broadcast Frame (Dispatched over `/topic/room.{roomId}`)**:
    ```json
    {
      "id": "76161474-9c0b-4ef8-bb6d-6bb9bd380a11",
      "roomId": "e3b0c442-9c0b-4ef8-bb6d-6bb9bd380a11",
      "senderId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
      "senderUsername": "testuser",
      "content": "Hello world!",
      "timestamp": "2026-07-11T03:50:00Z"
    }
    ```

### 4. Presence State Broadcast
When a user connects or disconnects, the server broadcasts their updated state to all rooms they are members of.
*   **Destination**: `/topic/presence.{roomId}`
*   **Broadcast Frame**:
    ```json
    {
      "userId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
      "username": "testuser",
      "status": "ONLINE",
      "timestamp": "2026-07-11T04:10:00Z"
    }
    ```

### 5. Typing Indicators
When a user begins or stops typing, the client publishes a transient typing state frame:
*   **Send Destination**: `/app/chat.typing`
*   **Payload**:
    ```json
    {
      "roomId": "e3b0c442-9c0b-4ef8-bb6d-6bb9bd380a11",
      "typing": true
    }
    ```
*   **Broadcast Destination**: `/topic/typing.{roomId}`
*   **Broadcast Frame**:
    ```json
    {
      "roomId": "e3b0c442-9c0b-4ef8-bb6d-6bb9bd380a11",
      "userId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
      "username": "testuser",
      "typing": true
    }
    ```

### 6. Message Read Receipts
When a user reads messages in a room, the client publishes their read marker:
*   **Send Destination**: `/app/chat.readReceipt`
*   **Payload**:
    ```json
    {
      "roomId": "e3b0c442-9c0b-4ef8-bb6d-6bb9bd380a11",
      "messageId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
    }
    ```
*   **Broadcast Destination**: `/topic/receipts.{roomId}`
*   **Broadcast Frame**:
    ```json
    {
      "roomId": "e3b0c442-9c0b-4ef8-bb6d-6bb9bd380a11",
      "userId": "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380b22",
      "messageId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
      "timestamp": "2026-07-11T04:15:00Z"
    }
    ```
