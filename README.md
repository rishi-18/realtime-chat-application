# Production-Grade Real-Time Chat Platform

A high-performance, stateless real-time chat backend built with **Spring Boot 3.x**, **WebSockets (STOMP)**, and **PostgreSQL**. Designed from the ground up utilizing enterprise-level scalability, clean architecture, and threat-modeled security principles.

---

## 🚀 Key Architectural Features

1.  **Stateless JWT Authentication & Session Refresh Rotation**:
    - Complete token lifecycle management with stateless JAX-RS filters.
    - Implements **Refresh Token Rotation (RTR)** to secure API access, preventing token replay attacks.
2.  **Multi-Tab User Presence Tracking**:
    - Event-driven tracking mapping active sessions per user using thread-safe segmented lock memory collections (`ConcurrentHashMap`).
    - Gracefully handles multiple browser tabs: status only transitions to `OFFLINE` when connection count reaches `0`.
3.  **Direct Messaging (1-to-1 chats) & Deterministic Names**:
    - Channel typing classifying channels as `PUBLIC_GROUP` or `DIRECT_MESSAGE`.
    - Generates unique DM channel names deterministically by sorting UUID strings alphabetically: `dm:{smaller-UUID}:{larger-UUID}`.
    - Prevents concurrent race conditions via index unique constraints fallback.
4.  **Real-Time Typing Indicators (Transient Relay)**:
    - Pure in-memory WebSocket event broadcasts bypassing disk I/O, supporting sub-millisecond propagation latencies.
    - Throttled client-side to prevent network packet storms.
5.  **Optimized Message Read Receipts (Read Watermarks)**:
    - Minimizes write-amplification by tracking a single read pointer (`last_read_message_id` on the `room_members` join table) per user session.
    - Eliminates high-volume database inserts: scales linearly to large group channels.
6.  **Threat-Modeled WebSocket Security**:
    - Inbound STOMP `SUBSCRIBE` interception verifies channel membership before subscribing, preventing subscription eavesdropping across all topics.
    - Validates typing and read receipt publishing endpoints against caller context.

---

## 🛠️ Tech Stack & Design Patterns

-   **Core**: Spring Boot 3, Java 17/20, Spring Security (Stateless).
-   **Messaging**: WebSockets + STOMP protocol.
-   **Database**: PostgreSQL persistent logs, Hibernate/JPA.
-   **Testing**: JUnit 5, Mockito (36 unit tests, clean mock stubbing).
-   **Patterns**: Single Responsibility (SRP), Dependency Inversion (DIP), Named parameter SQL binding, Read-pointer watermarks.

---

## 📂 Repository Structure

```text
├── docs/                     # Comprehensive enterprise-level architecture docs
│   ├── architecture.md       # Layered design details & service interactions
│   ├── system-design.md      # Bottlenecks, memory footprint & scale roadmaps
│   ├── database-design.md    # Normalized schemas & index strategies
│   ├── api-spec.md           # API endpoints & STOMP frame specs
│   └── decision-log.md       # Architect decision logs & trade-off matrices
├── src/
│   ├── main/
│   │   ├── java/com/chat/app/
│   │   │   ├── config/       # Web socket & JPA auditing configs
│   │   │   ├── controller/   # REST Controllers & Message endpoints
│   │   │   ├── dto/          # Data Transfer Objects & request models
│   │   │   ├── exception/    # Custom exception map handlers
│   │   │   ├── model/        # Entities (User, Room, RoomMember, Message)
│   │   │   ├── repository/   # JPA Repositories (with JPQL self-joins)
│   │   │   └── security/     # JWT Token filters & STOMP interceptors
│   └── test/                 # Test suites (36 Mockito tests)
```

---

## ⚙️ Running Locally

### 1. Prerequisites
- **Docker Desktop** (running)
- **Java 17 / 20 LTS**

### 2. Startup Databases
Initialize the PostgreSQL container:
```bash
docker compose up -d
```

### 3. Build & Test Compile
Execute the clean build and verify unit tests compile successfully:
```bash
$env:JAVA_HOME='C:\Program Files\Java\jdk-20'; .\mvnw clean test -Dtest=*Test
```

### 4. Boot Application
Launch the backend server:
```bash
$env:JAVA_HOME='C:\Program Files\Java\jdk-20'; .\mvnw spring-boot:run
```
The application will listen on `http://localhost:8080` (WebSocket endpoint upgrades at `ws://localhost:8080/ws`).
