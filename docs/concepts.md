# Concepts Guide

This document explains key backend, security, and database concepts introduced during Feature 1 and Feature 2 implementation.

---

## 1. WebSockets & STOMP Protocol (RFC 6455)

HTTP is unidirectional. WebSockets provide full-duplex communication over a single TCP connection.

### The Lifecycle
1.  **Handshake**: Client initiates an HTTP request with specific headers:
    - `Upgrade: websocket`
    - `Connection: Upgrade`
    - `Sec-WebSocket-Key: <base64>`
2.  **Upgrade**: The server responds with `HTTP/1.1 101 Switching Protocols`. The TCP socket remains open, allowing frames to flow bi-directionally without the overhead of HTTP headers.
3.  **STOMP Frame Protocol**: Over this raw connection, clients exchange STOMP frames:
    ```text
    SEND
    destination:/app/chat.sendMessage
    content-type:application/json

    {"roomId":"e3b0c442...","content":"Hello!"}^@
    ```
    - The frame consists of a Command, Headers (key-value), a blank line, a body payload, and a null character (`^@`).

---

## 2. Spring ChannelInterceptors
Spring WebSocket architecture uses message channels:
- `clientInboundChannel`: Receives messages from clients.
- `clientOutboundChannel`: Sends messages to clients.
- `brokerChannel`: Sends messages to the broker.

We can intercept these channels using a `ChannelInterceptor` (e.g., `preSend`):
- This allows us to intercept a STOMP `CONNECT` frame, extract the authorization header, validate the JWT, and authenticate the session before the request proceeds.
- We also intercept `SUBSCRIBE` frames to ensure the user is an active member of the target channel, preventing unauthorized access.

---

## 3. Database Indexes: B-Tree & Composite Indexing

Indexes speed up data retrieval at the cost of disk space and write overhead.

### 1. B-Tree Indexes
PostgreSQL uses B-Trees (Balanced Trees) by default. They keep data sorted and allow search, sequential access, insertions, and deletions in logarithmic time $O(\log n)$.
- We index `users(email)` and `users(username)` to ensure O(1)-like login and lookup checks.

### 2. Composite Indexes
A composite index covers multiple columns (e.g., `messages(room_id, created_at DESC)`).
- **Sorting Performance**: When querying message history for a room, we run:
  ```sql
  SELECT * FROM messages WHERE room_id = ? ORDER BY created_at DESC LIMIT 50;
  ```
  If we only index `room_id`, the database must fetch all messages for the room and sort them in memory.
  By creating a composite index on `(room_id, created_at DESC)`, the index stores the records pre-filtered and pre-sorted on disk. The database traverses the B-Tree directly to the matching node and reads the next 50 records sequentially.
- **Column Order**: The order of columns in a composite index is critical. The column used with equality operators (`room_id = ?`) must come first, followed by columns used for range queries or sorting (`created_at`).

---

## 4. Spring ApplicationEvent Multicaster & WebSocket Session Events

Spring provides an event-driven model using `ApplicationEvent` and `ApplicationListener`.
In a WebSocket application:
1.  **SessionConnectEvent**: Published when a new STOMP client sends a CONNECT frame. Spring upgrades the session and maps the user's principal attributes.
2.  **SessionDisconnectEvent**: Published when a client session ends (due to network timeout, socket termination, or browser tab closure).

By defining a listener class (`@Component` implementing or declaring `@EventListener`), we can intercept these lifecycle events to register online and offline transitions. These event listeners are executed on Spring's core message dispatcher thread pool.

---

## 5. Concurrent Collections: ConcurrentHashMap Thread-Safety

To track presence without database writes, we store active online sessions in memory.
- **Why not standard HashMap?**: `HashMap` is not thread-safe. Concurrent modifications (multiple threads reading and writing connections simultaneously) can corrupt internal hash buckets, resulting in infinite loops, CPU spikes, or `ConcurrentModificationException` failures.
- **ConcurrentHashMap**: Uses segmented locks (or CAS - Compare-And-Swap operations on bucket heads in modern Java) to allow concurrent readers and writers to access different hash bins without blocking each other. This provides high concurrency reads and writes at $O(1)$ complexity.

---

## 6. SQL Relational Intersection Queries (Finding DM Rooms)

When User A wants to start a Direct Message (DM) chat with User B, the backend must verify if a DM room already exists between them.
Because room memberships are modeled as rows in a join table (`room_members`), this query represents a **relational intersection problem** (finding the room ID where both users are members, and the room type is `DIRECT_MESSAGE`).

In SQL, this is optimized using a **self-join** on the membership table, checking that both rows map to the same room:
```sql
SELECT rm1.room_id 
FROM room_members rm1
JOIN room_members rm2 ON rm1.room_id = rm2.room_id
JOIN rooms r ON r.id = rm1.room_id
WHERE r.room_type = 'DIRECT_MESSAGE'
  AND rm1.user_id = :userA
  AND rm2.user_id = :userB;
```

### Execution Strategy
1.  **Index Traversal**: The database engine performs a B-Tree index lookup for `rm1.user_id = :userA` using the secondary index `idx_room_members_user`.
2.  **Join Constraints**: For each room matching User A, it performs a nested-loop index lookup on the composite primary key `(room_id, user_id)` of `rm2` checking for `user_id = :userB`.
3.  **Type Check**: It joins the corresponding row in `rooms` to verify that `room_type = 'DIRECT_MESSAGE'`.
This executes in $O(\log N)$ logarithmic time, bypassing full table scans.

---

## 7. Client-Side Rate-Limiting: Throttling & Debouncing

High-frequency real-time actions (like user typing notifications or window resizes) can quickly overwhelm network buffers. To prevent flooding, systems implement rate-limiting strategies:

1.  **Throttling**: Enforces a maximum execution frequency. If a function is throttled to 3 seconds, it executes immediately on the first call, but subsequent calls within that 3-second window are discarded or delayed.
    - *Usage*: Typing indicator heartbeats. Once a user starts typing, the server is notified. Subsequent keystrokes are throttled; the client only sends a ping every 3 seconds to confirm they are still active.
2.  **Debouncing**: Delays execution until a specified period of inactivity has elapsed. If the function is debounced to 1 second, it will not fire until 1 second after the user has completely stopped calling it.
    - *Usage*: Typing cancellation. When the user stops typing, the client debounces for 3 seconds before sending the `typing = false` frame to inform peers they have stopped.

---

## 8. Read Receipts Storage Optimization (Reducing Write Amplification)

In high-concurrency chat systems, tracking which messages have been read by which users can generate enormous write amplification:
- **Naive Approach**: Create a row in a `message_receipts` table for *every message* for *every user*. In a room with 100 members, sending a single message generates 100 database writes as members open the room and read it. At scale, this quickly saturates the transaction pool.
- **Optimized Approach (Read Pointer)**: Since messages in a room are ordered sequentially by creation time (or auto-incrementing sequences), we only need to track the **last read message ID** (or timestamp) per user per room.
  - We store this in the `room_members` table as `last_read_message_id`.
  - When a user reads the latest message, we update their single member record.
  - This collapses hundreds of individual row inserts into a single SQL `UPDATE` statement per user session.
  - To find if a specific message has been read by User X, we check if the message's creation date/ID is less than or equal to User X's last read message. This reduces database write loads by orders of magnitude.

---

## 9. Media Attachment Handling & Storage Integration

Modern real-time systems handle file and media attachments securely using decoupled upload pipelines:

1.  **Multipart File Ingestion**: Files are uploaded via REST (`multipart/form-data`) as `MultipartFile` streams.
2.  **Mime-Type Auditing (Magic Numbers)**: Verifying file extensions (e.g. checking `.jpg`) is vulnerable to spoofing. Attackers can rename executable scripts (`shell.sh`) to images (`shell.jpg`) to bypass filters.
    - *Mitigation*: The backend parses the **magic numbers** (first few bytes of the file stream) using Java's `Files.probeContentType()` or Apache Tika to determine the authentic content type before saving.
3.  **Local Storage vs Object Storage**:
    - **Local Block Storage**: Uploads are saved to the server's local file system (e.g., `/var/www/uploads`). It is simple to implement but has severe horizontal limitations (instances in a cluster do not share storage, and local disks are ephemeral).
    - **Cloud Object Storage (S3)**: Files are written directly to S3 or Google Cloud Storage. Gateways generate **Pre-signed URLs** allowing clients to upload directly to S3 buckets, bypassing the application server to eliminate bandwidth bottlenecks.
4.  **CDN Integration**: Attachment URLs reference a Content Delivery Network (e.g., CloudFront, Cloudflare) rather than raw S3 buckets, caching media at edge caches globally for sub-millisecond download times.
