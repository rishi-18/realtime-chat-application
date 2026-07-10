# Systems Design & Coding Interview Notes

This document catalogs advanced backend, messaging, database concurrency, and security interview questions with detailed model answers.

---

## 1. System Design Interview Q&A

### Question 1: How do you design a real-time chat platform to scale horizontally when using WebSockets?
**Model Answer**:
WebSockets maintain a stateful, persistent TCP connection between the client and a specific server instance. This statefulness introduces scaling challenges compared to stateless HTTP:
1.  **Shared Message Broker (Broker Relay)**: If User A is connected to Node 1 and User B is connected to Node 2, and they are in the same room, Node 1 cannot directly send messages to User B.
    - *Solution*: Introduce a centralized message broker (e.g. RabbitMQ or Redis Pub/Sub). When Node 1 receives a message, it publishes it to a channel on the broker. All cluster nodes subscribe to this broker channel. Node 2 receives the event via the broker and relays the message to User B over its active WebSocket connection.
2.  **Load Balancing**: Standard load balancers must support sticky sessions or TCP connection balancing. They must upgrade HTTP connections and hold long-lived connections.
3.  **Connection Limits**: Servers are bound by file descriptors. We must tune the OS limits (`ulimit -n`) and transition to non-blocking I/O (e.g., Netty, WebFlux) to manage hundreds of thousands of open sockets with minimal CPU/Memory overhead.

### Question 2: How do you implement secure authorization for WebSocket channels (topics)?
**Model Answer**:
We secure WebSocket topics by authenticating the connection and authorizing subscriptions:
1.  **Authentication during Handshake/Connect**: Since standard browser WebSockets do not support custom HTTP headers, we intercept the STOMP `CONNECT` frame and extract the JWT token from the STOMP headers. If the token is valid, we inject the authentication principal into the WebSocket session context.
2.  **Authorization on Subscription**: An authenticated user should only receive messages from rooms they belong to. When the client sends a `SUBSCRIBE` frame to `/topic/room.{roomId}`, our custom `ChannelInterceptor` intercepts the request, extracts the `roomId`, and queries the database (or cache) to verify the user is a member of the room. If the check fails, the interceptor throws an `AccessDeniedException`, blocking the subscription.

---

## 2. Database Concurrency Q&A

### Question 3: Explain the query optimization benefit of a composite index on `(room_id, created_at DESC)`.
**Model Answer**:
When fetching chat history, we query:
```sql
SELECT * FROM messages WHERE room_id = ? ORDER BY created_at DESC LIMIT 50;
```
If we only index `room_id`, the database engine will perform a B-Tree search to find all matching rows. It must then copy these rows into memory (temp space) and perform a sorting algorithm (like QuickSort) to order them by `created_at DESC` before returning the top 50. This in-memory sort becomes a bottleneck as message history grows.

By creating a composite index on `(room_id, created_at DESC)`:
1.  The index groups records on disk first by `room_id`.
2.  Within each `room_id` partition, the records are pre-sorted in descending order of `created_at`.
3.  The database engine executes the query by performing a B-Tree lookup on `room_id` and reading the first 50 pre-sorted records sequentially. This eliminates the CPU and memory cost of sorting, executing in $O(\log N)$ time rather than $O(N \log N)$ or worse.

---

## 3. High-Throughput Presence Interview Q&A

### Question 4: How do you design a real-time presence system (online/offline status) to scale to 10 million daily active users (DAUs)?
**Model Answer**:
Scaling presence status requires addressing write bottlenecks and broadcast storms:
1.  **Write Bottleneck Mitigation (Redis Caching)**: Do not write presence updates to a relational database. We write to a centralized **Redis cluster**. We store status using key-value pairs (e.g. `user:presence:{userId} -> "ONLINE"`) with a Time-To-Live (TTL) of 30 seconds.
2.  **Heartbeat Protocol (Keep-Alives)**: Clients send ping heartbeats every 10-20 seconds. The server receives the ping, updates the Redis key TTL back to 30 seconds. If a client disconnects cleanly, we delete the key. If a client drops offline abruptly (e.g. entering a tunnel), the TTL expires, and the user is implicitly marked offline.
3.  **Broadcast Storm Reduction**: Rather than broadcasting every status change to all rooms a user belongs to (push model), implement a hybrid approach:
    - **Active Poll (Pull Model)**: The client query status only for currently visible users in their active UI viewport.
    - **Presence Throttling / Batching**: If push is necessary (e.g. high priority DM list), batch status changes and send updates every 5-10 seconds instead of instantly.

---

## 4. Direct Messaging & SQL Joins Interview Q&A

### Question 5: How do you write and optimize a SQL query to check if a 1-to-1 Direct Message room already exists between two users in a many-to-many relationship schema?
**Model Answer**:
We optimize this relational intersection query by using a table self-join on the many-to-many table (`room_members`), matching both users to the same room:
```sql
SELECT rm1.room_id 
FROM room_members rm1
JOIN room_members rm2 ON rm1.room_id = rm2.room_id
JOIN rooms r ON r.id = rm1.room_id
WHERE r.room_type = 'DIRECT_MESSAGE'
  AND rm1.user_id = :userA
  AND rm2.user_id = :userB;
```
**Why this is highly performant**:
- **Index nested loops**: The query utilizes the index on `user_id` to quickly locate rooms for User A. It then loops over those matches and executes a binary search check on the composite primary key `(room_id, user_id)` of the index for User B.
- **Constant limit**: Since this is a 1-to-1 relationship check, we can append `LIMIT 1` to instantly abort scan executions as soon as the first matching record is found.
- **Memory footprint**: The database engine performs index operations directly without temp tables or sorting buffers, running in $O(\log N)$ logarithmic time.

---

## 5. Scaling Transient Event Streams Interview Q&A

### Question 6: How do you design and scale a "User is typing..." indicator feature for 10 million concurrent active WebSocket sessions?
**Model Answer**:
Typing indicators generate very high event volume. If 1 million users type simultaneously, we can see peaks of 100k+ typing pings per second.
1.  **Completely Bypass Database writes**: Under no circumstances should typing events trigger database inserts or updates. The server should act as a pure, stateless message relay.
2.  **Edge Routing & Broker Relays**: WebSocket connections are terminated at edge gateways. If User A types in Instance 1, and peers are in Instance 2, Instance 1 relays the frame to a fast, shared in-memory broker (such as Redis Pub/Sub). Peers' instances receive it from Redis and stream it out. Since Redis executes pub/sub in RAM, it scales easily to hundreds of thousands of operations per second.
3.  **Client-Side Throttling and Debouncing**: Protect the network by enforcing client rate limits. The client triggers a WebSocket message only when typing transitions from `false` to `true`, and then uses a 3-second throttle timer to send pings. If the user stops typing, a debounced handler fires 3 seconds later, sending a `typing = false` event.
4.  **Gateway-Level Security Caching**: Checking group membership inside SQL databases during high-frequency typing streams will saturate connection pools. Gateways should verify and cache user channel memberships in a fast local cache or Redis hash with short TTL (e.g. 5 minutes) to perform membership checks at $O(1)$ speeds.

---

## 6. Real-Time Read Receipts Interview Q&A

### Question 7: How do you design a message read receipt tracking system for a large-scale chat application like Slack to avoid write amplification issues?
**Model Answer**:
The naive implementation of tracking receipts on a per-user per-message row basis results in severe write amplification. For a channel containing 1,000 members, reading a single message generates 1,000 database insert operations.
1.  **Read Pointers (Watermarks)**: Instead of tracking every message receipt, track a single **read watermark** per user per channel (e.g., storing `last_read_message_id` or `last_read_at` timestamp). Because messages are ordered sequentially, if User X has read message #150, they have implicitly read all messages #1 to #149. This collapses 1,000 row inserts into a single SQL `UPDATE` statement per member.
2.  **Write Coalition (Batching Updates)**: When users scroll through a chat history, do not fire a write request on every message. The client throttles read pointer pings (e.g., updating the watermark only when the user stops scrolling for 1 second, or batching updates in 2-second windows).
3.  **Read-Aside Cache**: Store read watermarks in an active cache (e.g., Redis). When rendering chat rooms, the server queries watermarks from Redis at $O(1)$ speed. Watermarks are asynchronously flushed to PostgreSQL using write-behind queue workers.
