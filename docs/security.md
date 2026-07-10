# Security Architecture & Threat Model

This document maps out the security model, threat mitigations, and WebSocket session protections for the Chat Platform.

---

## 1. WebSocket Authorization Interception

Because browsers do not support custom HTTP headers in raw WebSocket connections, standard Bearer token header injection is not available.

```text
       [Client CONNECT Frame]
                 │
                 ▼
     [STOMP Inbound Channel]
                 │
                 ▼
    [ChannelInterceptor.preSend()]
      ├── Inspect Frame Command
      │     ├── If CONNECT -> Validate JWT in header, set SecurityContext
      │     └── If SUBSCRIBE -> Check if User is in room_members table
      ▼
   [Authorized STOMP Routing]
```

This prevents unauthorized clients from connecting or subscribing to topics they do not have access to (e.g., trying to read messages from a private room ID by guessing its UUID).

---

## 2. Threat Modeling & Mitigation Matrix

| Threat | Attack Vector | Mitigation in Codebase |
| :--- | :--- | :--- |
| **Eavesdropping on Private Rooms** | Attackers subscribe directly to `/topic/room.{roomId}`. | Custom `ChannelInterceptor` intercepts the `SUBSCRIBE` command, parses the destination, and checks the database to verify the user is a member of the room. |
| **Topic Subscription Leaks** | Attackers subscribe to `/topic/typing.{roomId}`, `/topic/receipts.{roomId}`, or `/topic/presence.{roomId}`. | Handled: `JwtChannelInterceptor` intercepts and checks room membership for all room prefixes (`room`, `typing`, `receipts`, `presence`), rejecting unauthorized subscriptions. |
| **Typing Indicator Spoofing** | Attackers send fake typing indicators to rooms they don't belong to. | Handled: `MessageController` verifies room membership for all `/app/chat.typing` send pings before relaying. |
| **Cross-Site Scripting (XSS)** | Attacker broadcasts HTML/JS scripts in chat messages. | Enforce strict JSR-380 input validation. The client frontend must escape content before rendering payloads to prevent DOM injection. |
| **Denial of Service (DoS) via Sockets** | Malicious clients open thousands of WebSocket connections without authenticating. | The connection interceptor disconnects sockets if the STOMP `CONNECT` frame is not received and authenticated within a short window (e.g., 5 seconds). |
| **SQL Injection** | Injecting malicious code through chat message content. | We use JPA/Hibernate with named parameters (`:roomId`, `:userId`), which automatically parameters-bind and sanitize input strings. |
| **Token Theft via URL Logs** | Passing JWT in WebSocket handshake query parameters. | Handled: We pass the JWT securely in the header metadata of the STOMP `CONNECT` frame rather than the handshake URL, preventing the token from being logged in plain text by reverse proxies. |
