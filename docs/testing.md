# Testing Framework & Strategy

This document details unit, integration, and E2E testing strategies for the Chat Platform.

---

## 1. Real-Time Testing Strategies

### 1. Business Logic Unit Tests
We use JUnit 5 and Mockito to test service layers (e.g., `RoomService` and `MessageService`), mocking database actions to verify validation rules and exception routing.

### 2. WebSocket Integration Tests
Testing real-time features requires executing STOMP connection pipelines.
- **Tools**: We use `WebSocketStompClient` and `StandardWebSocketClient` in Spring Boot integration tests.
- **Flow**:
  - Start the application context on a random port (`WebEnvironment.RANDOM_PORT`).
  - Establish a connection to `ws://localhost:${port}/ws`.
  - Pass a valid JWT in the STOMP connection headers.
  - Subscribe to `/topic/room.{roomId}`.
  - Send a message to `/app/chat.sendMessage` and verify it is received on the subscription channel within a timeout (e.g., 2 seconds).

---

## 2. Unit Testing Suite (36 Mockito Unit Tests)

To verify the correct behavior of our business layers and WebSocket mappings, we maintain a comprehensive unit test suite:
1.  **MessageControllerTest (6 tests)**: Verifies successful real-time message routing, typing indicators, read receipts, and validates that anonymous pings are discarded.
2.  **RoomServiceTest (13 tests)**: Verifies room creations, memberships, direct messaging name sorting, and out-of-order read pointer updates.
3.  **PresenceEventListenerTest (3 tests)**: Mocks connection events and disconnect listeners, verifying that room presence is broadcast cleanly.
4.  **AuthServiceTest (3 tests)**, **MessageServiceTest (4 tests)**, **PresenceServiceTest (4 tests)**, and **UserServiceTest (3 tests)**.

---

## 3. Concurrency & Performance Tests

To verify stability under concurrent load:
1.  **Subscription Eavesdropping Tests**: Verify that subscribing to a room without joining returns an error and closes the subscription frame.
2.  **Concurrency Testing (Room Joining)**: Verify that simultaneous requests by a user to join a room do not create duplicate records.
3.  **Load Testing (Gatling/K6)**: Run simulations to scale open socket connections to 10k+, verifying memory utilization profiles and DB connection pool stability.
