# Architectural Decision Log (ADL)

This document tracks engineering design decisions, alternatives, and rationales for the Chat Platform.

---

## Decision 1: Stateless JWT over Stateful Redis Session Store
- **Context**: Choosing session strategy.
- **Decision**: Stateless JWT with RTR (Refer to previous log for criteria details).

---

## Decision 2: Symmetric HMAC-SHA256 (HS256) over Asymmetric RS256 for Initial Release
- **Context**: Choosing signing algorithm.
- **Decision**: Symmetric HS256 (Refer to previous log for criteria details).

---

## Decision 3: Custom Exception Handler JSON Payloads
- **Context**: REST exceptions standard.
- **Decision**: RestControllerAdvice custom mappings (Refer to previous log for criteria).

---

## Decision 4: Spring STOMP over Raw WebSockets Handlers

- **Context**: Designing the real-time application sub-protocol layer.
- **Date**: 2026-07-11
- **Status**: Accepted
- **Alternatives Considered**:
  - **Option A: Raw WebSocket Handler (`Binary` or `Text` frames)**.
  - **Option B: STOMP Sub-Protocol on top of WebSockets**.

### Trade-off Matrix

| Criteria | Option A (Raw WebSockets) | Option B (STOMP Sub-Protocol) [Chosen] |
| :--- | :--- | :--- |
| **Routing Protocol** | Custom (Must write custom action routers) | **Standardized (Built-in destinations like /topic)** |
| **Connection Lifecycle** | Custom ping/pong logic required | **Standardized heartbeats out-of-the-box** |
| **Pub/Sub Broker Integration** | Hard (Must build thread-safe registry) | **Easy (Integrates directly with Spring Message Broker)** |
| **Frame Payload Overhead** | **Very Low** | Low (Minor headers overhead) |

### Final Decision & Rationale
We chose **Option B (STOMP)**. Writing a custom framing and routing protocol on top of raw WebSockets creates unnecessary maintenance overhead. STOMP is a mature, lightweight text protocol that provides standardized frames (`CONNECT`, `SUBSCRIBE`, `SEND`, `MESSAGE`) out-of-the-box. Spring provides native support for mapping STOMP destinations to controllers using `@MessageMapping`, saving development time and reducing security integration errors.

---

## Decision 5: Synchronous vs. Asynchronous Database Storage for Messages

- **Context**: Deciding when to persist messages to SQL relative to broadcasting them to online subscribers.
- **Date**: 2026-07-11
- **Status**: Accepted
- **Alternatives Considered**:
  - **Option A: Synchronous Database Persistence**.
  - **Option B: Asynchronous Queueing (Outbox Pattern / Event Bus)**.

### Trade-off Matrix

| Criteria | Option A (Synchronous Persistence) [Chosen] | Option B (Asynchronous Queueing) |
| :--- | :--- | :--- |
| **System Latency** | Higher (Requires DB write before broadcast) | **Lower (Instant in-memory broadcast)** |
| **Durability Guarantee** | **Guaranteed (Messages saved if broadcast)** | Eventual Consistency (Risk of failure to persist) |
| **Infrastructure Overhead**| **Low (Uses JDBC connection pool directly)** | High (Requires Kafka/RabbitMQ instance and worker pools) |

### Final Decision & Rationale
We chose **Option A (Synchronous Persistence)** for the initial core release. This ensures absolute consistency: clients only see a message on their screen if it has been successfully persisted in PostgreSQL. It avoids the infrastructure overhead of managing an external message queue. As traffic scales to millions of users, we will migrate to Option B, utilizing Kafka to handle high-frequency database writes asynchronously.

---

## Decision 6: User Presence Storage Strategy

- **Context**: Deciding where to store the online/offline status of users.
- **Date**: 2026-07-11
- **Status**: Approved
- **Alternatives Considered**:
  - **Option A: In-Memory Cache (ConcurrentHashMap)**.
  - **Option B: Database Persistence (PostgreSQL status column updates)**.
  - **Option C: Centralized Distributed Cache (Redis)**.

### Trade-off Matrix

| Criteria | Option A (In-Memory Cache) [Chosen] | Option B (Database updates) | Option C (Redis Cache) |
| :--- | :--- | :--- | :--- |
| **Write/Read Latency**| **Sub-microsecond (Local RAM)** | High (Disk write constraints) | Low (In-memory network socket) |
| **Node Sharing** | None (Single server context) | **Shared (Relational schema)** | **Shared (Centralized network cache)** |
| **Resilience** | None (State lost on restart) | **High (Persistent disk storage)** | Medium (In-memory with optional snapshots) |
| **System Overhead** | **None (Standard JVM memory)** | High (Disk I/O and transaction locks)| Low (Requires separate Redis container) |

### Final Decision & Rationale
We chose **Option A (In-Memory Cache)** utilizing a thread-safe `ConcurrentHashMap` for the initial implementation. Storing presence states in PostgreSQL (Option B) is an anti-pattern due to high write-amplification caused by rapid connection/disconnection events. While Option C (Redis) is the target pattern for horizontal scalability, choosing Option A allows us to build the presence system core interface-driven, allowing us to swap in a Redis implementation when clustered architecture is introduced.

---

## Decision 7: Direct Message Modeling Strategy

- **Context**: Deciding how to represent Direct Messages (DMs) in the schema.
- **Date**: 2026-07-11
- **Status**: Approved
- **Alternatives Considered**:
  - **Option A: Unified Room Entity with RoomType enum [Chosen]**.
  - **Option B: Separate Entity Model (DirectMessage / DMRoom)**.

### Trade-off Matrix

| Criteria | Option A (Unified Entity) [Chosen] | Option B (Separate Entity) |
| :--- | :--- | :--- |
| **Code Reuse** | **High** (Reuses `messages`, `room_members`, and interceptor authorization filters) | Low (Requires writing separate tables, entities, services, and STOMP channels) |
| **Query Complexity** | Medium (Requires self-join on room_members to find existing pairs) | **Low** (Simple lookup on a unique composite user pair table) |
| **Schema Flexibilty**| **High** (Unified message history logs and pagination hooks) | Low (Limits unified message querying) |

### Final Decision & Rationale
We chose **Option A (Unified Room Entity)**. By adding a `roomType` enum to our existing `Room` entity, we can fully reuse all message persistence and STOMP interceptor security checks. To guarantee uniqueness of DM pairs and prevent duplicate room creations, we enforce a deterministic naming convention for DMs: `dm:{smaller-user-uuid}:{larger-user-uuid}`. This combines maximum code reuse with a mathematically clean uniqueness guarantee.

---

## Decision 8: Typing Indicators Routing & Caching

- **Context**: Deciding how to route and authorize high-frequency typing indicator events.
- **Date**: 2026-07-11
- **Status**: Approved
- **Alternatives Considered**:
  - **Option A: Stateless In-Memory Relay with Database Verification [Chosen]**.
  - **Option B: Distributed Cache-Backed Relay (Redis membership caching)**.

### Trade-off Matrix

| Criteria | Option A (Database Verification) [Chosen] | Option B (Redis Caching) |
| :--- | :--- | :--- |
| **Membership Check Speed** | Low (Queries PostgreSQL table) | **High (O(1) Redis hash check)** |
| **System Complexity** | **Low (No extra infrastructure)** | Medium (Requires Redis cache infrastructure) |
| **Event Propagation Speed**| **Sub-1ms (Direct memory channel relay)** | Low (Requires round-trip to Redis) |

### Final Decision & Rationale
We chose **Option A (Stateless In-Memory Relay with database membership check)** for the initial typing indicator execution. Although running database queries (`existsByIdRoomIdAndIdUserId`) for every typing ping can create load at scale, our client-side throttling (restricting typing pings to once every 3 seconds) keeps event frequency low enough to protect PostgreSQL under moderate loads. Option A does not require setting up a Redis cache instance. As traffic scales to millions of concurrent sessions, we will swap our membership check to Option B (Redis hash mappings) to bypass database queries entirely.

---

## Decision 9: Read Receipt Storage Strategy

- **Context**: Deciding how to track message read status for users in rooms.
- **Date**: 2026-07-11
- **Status**: Approved
- **Alternatives Considered**:
  - **Option A: User Read Pointer Watermark on RoomMember (last_read_message_id) [Chosen]**.
  - **Option B: Per-User Per-Message Mapping Rows**.

### Trade-off Matrix

| Criteria | Option A (Read Pointer Watermarks) [Chosen] | Option B (Message Mapping Rows) |
| :--- | :--- | :--- |
| **Write Load** | **Extremely Low** (Single `UPDATE` row statement per user session) | High (Multiple `INSERT` row updates per message read) |
| **Query Speed** | **High** (Comparison index check: `message.id <= member.last_read`) | Low (Scans mapping records join table) |
| **Relational Integrity**| **High** (Maintains direct mapping to `RoomMember` records) | Medium (Increases table space requirements) |

### Final Decision & Rationale
We chose **Option A (Read Pointer Watermarks)**. Storing a `last_read_message_id` on the `room_members` table collapsing all prior message reads into a single cell pointer completely avoids high-frequency row inserts. It scales efficiently to large group channels since write operations are flat-rate updates rather than multiplying on message frequency.

---

## Decision 10: Media & Attachment Upload Architecture

- **Context**: Deciding how to ingest, validate, and store file attachments.
- **Date**: 2026-07-11
- **Status**: Approved
- **Alternatives Considered**:
  - **Option A: Pre-signed cloud S3 PUT URLs**.
  - **Option B: Backend Proxy Uploads (REST Multipart Local Storage) [Chosen]**.

### Trade-off Matrix

| Criteria | Option A (Pre-signed S3 URLs) | Option B (Backend Proxy Uploads) [Chosen] |
| :--- | :--- | :--- |
| **Server Bandwidth Load**| **Extremely Low** (Direct client-to-cloud streams) | High (Streams pass through application memory) |
| **System Complexity** | High (Requires AWS S3 configurations & tokens) | **Low (Uses local directories directly)** |
| **Access Controls** | Medium (Requires S3 IAM Bucket policies) | **High (Backend handles authorization checks)** |

### Final Decision & Rationale
We chose **Option B (Backend Proxy Uploads)** storing files in a local folder for the initial core release. This eliminates cloud account setup dependencies and keeps local development simple. We enforce security validations (10MB limits, mime-type verification) inside Spring. When horizontal scaling is required, we will transition to Option A to protect server CPU and bandwidth.

---

## Decision 11: Message Deletion Pattern

- **Context**: Deciding how to remove message data when requested by a user.
- **Date**: 2026-07-11
- **Status**: Approved
- **Alternatives Considered**:
  - **Option A: Hard-deletion (SQL DELETE) [Rejected]**.
  - **Option B: Soft-deletion (is_deleted flag + content nullification) [Chosen]**.

### Trade-off Matrix

| Criteria | Option A (Hard-deletion) | Option B (Soft-deletion) [Chosen] |
| :--- | :--- | :--- |
| **Referential Integrity**| Low (Risk of breaking foreign keys) | **High (Foreign keys remain intact)** |
| **Audit Trails** | Low (Permanently erases data) | **High (Preserves historical log indicators)** |
| **Data Cleanup** | **High** (Reclaims disk space instantly) | Low (Keeps rows, requiring archiving jobs) |

### Final Decision & Rationale
We chose **Option B (Soft-deletion)**. Preserving relational links (e.g. read pointers in `room_members`) is crucial to prevent foreign key errors. Setting `is_deleted = TRUE` and clearing content/attachments keeps database history coherent while respecting user deletion requests. For compliance, we will implement archiving tasks to compress or move older soft-deleted rows out of active operational tables.

---

## Decision 12: Message Reactions Query Aggregation

- **Context**: Deciding how to retrieve and group emoji reactions when returning message history pages.
- **Date**: 2026-07-11
- **Status**: Approved
- **Alternatives Considered**:
  - **Option A: `@OneToMany(fetch = FetchType.EAGER)` mapping [Rejected]**.
  - **Option B: Batch Querying (`IN (:messageIds)`) with In-Memory grouping [Chosen]**.

### Trade-off Matrix

| Criteria | Option A (Eager Mappings) | Option B (Batch Query + In-Memory Map) [Chosen] |
| :--- | :--- | :--- |
| **Database Round-trips**| **1 query** (Uses SQL JOIN) | 2 queries (1 for messages, 1 for reactions) |
| **Data Redundancy** | High (Cartesian product row duplicates) | **Extremely Low** (Returns flat tables) |
| **Query Latency** | High (Database joining overhead) | **Extremely Low** (Parallel index scans) |

### Final Decision & Rationale
We chose **Option B (Batch Querying with In-Memory grouping)**. While Option A fetches everything in a single SQL query, joins containing multiple collection columns (e.g. messages with attachments AND reactions) trigger cartesian product row duplication, bloating network overhead and JVM memory. Performing a single batch query (`IN (:messageIds)`) resolves the N+1 query problem, keeping database traffic to exactly **two queries** per history request.

---

## Decision 13: Message Search Indexing Implementation

- **Context**: Deciding the technical indexing approach for full-text search across channel logs.
- **Date**: 2026-07-11
- **Status**: Approved
- **Alternatives Considered**:
  - **Option A: External Search Engine (Elasticsearch/OpenSearch) [Rejected for MVP]**.
  - **Option B: PostgreSQL Native Full-Text Search with GIN Index [Chosen]**.

### Trade-off Matrix

| Criteria | Option A (Elasticsearch) | Option B (PostgreSQL GIN) [Chosen] |
| :--- | :--- | :--- |
| **Operational Complexity**| High (Requires clustering, sync piping, CDC setup) | **Extremely Low** (Native database feature) |
| **Write Latency Penalty** | **None** (Async index ingestion) | High (Index write amplification on main DB) |
| **Search Query Performance** | **High** (Dedicated inverted index cluster) | Medium-High (Logarithmic index scans on single DB) |

### Final Decision & Rationale
We chose **Option B (PostgreSQL Native Full-Text Search with GIN Index)**. For our current scale and development velocity, setting up a standalone Elasticsearch cluster and a Kafka/CDC sync pipeline represents excessive engineering overhead. Native PostgreSQL FTS with GIN indexes meets all response-time SLA bounds, simplifies deployments, and ensures immediate transactional search visibility. As the application grows to hundreds of millions of rows, we will scale out by migrating to Option A via async stream replication.

---

## Decision 14: User Mentions Extraction Parsing Strategy

- **Context**: Deciding how to extract `@username` tokens to store mentions on message dispatch.
- **Date**: 2026-07-11
- **Status**: Approved
- **Alternatives Considered**:
  - **Option A: Client-side parsing with ID arrays in REST payloads [Rejected]**.
  - **Option B: Server-side regex parsing of message content [Chosen]**.

### Trade-off Matrix

| Criteria | Option A (Client IDs) | Option B (Server Regex) [Chosen] |
| :--- | :--- | :--- |
| **Tamper Resistance**| Low (Clients can forge target IDs) | **High (Server controls parsing logic)** |
| **API Cleanliness** | Low (Payloads must bundle custom arrays) | **High (Payloads remain simple strings)** |
| **Server CPU load** | **Low** (No text scanning) | Medium-Low (Regex match evaluations on insert) |

### Final Decision & Rationale
We chose **Option B (Server-side regex parsing)**. Relying on clients to report target user IDs introduces a critical exploit vector: a user could script requests injecting arbitrary user IDs to trigger notifications without their username appearing in the message text. Doing server-side regex scans guarantees that mentions maps correspond exactly to text body patterns, keeping the payload schema simple.
