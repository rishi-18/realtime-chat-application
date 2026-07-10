# Glossary of Terms

This glossary defines core backend engineering, security, and messaging terms introduced in the project.

---

| Term | Definition |
| :--- | :--- |
| **ACID** | Atomicity, Consistency, Isolation, Durability. Properties guaranteeing relational transactions are executed reliably. |
| **BCrypt** | Adaptive key derivation password hashing function incorporating random salting and cost factor loops. |
| **Broker Relay** | A shared message broker backing clusters of application servers to forward broadcast events across instances. |
| **ChannelInterceptor** | A Spring Security hook enabling interception and modification of STOMP message frames on inbound/outbound channels. |
| **Composite Index** | A database index targeting multiple columns in a specific order, optimizing filter-sort queries. |
| **Connection Upgrade** | The process where a client upgrades an HTTP connection to a full-duplex TCP WebSocket connection. |
| **File Descriptor** | An abstract handle used by an operating system to access files, directories, or network sockets. |
| **Full-Duplex** | Bi-directional communication where data flows simultaneously in both directions over a single channel. |
| **MDC** | Mapped Diagnostic Context. ThreadLocal map storing diagnostic parameters for logging frameworks. |
| **N+1 Query Problem** | A query performance issue where an ORM executes $N$ additional select statements to fetch lazy associations. |
| **RTR** | Refresh Token Rotation. A session protection pattern that rotates refresh tokens on every refresh operation. |
| **SimpleBroker** | An in-memory message broker provided by Spring WebSockets to route message broadcasts. |
| **STOMP** | Simple Text Oriented Messaging Protocol. A text-based messaging protocol that runs on top of WebSockets, providing framing and routing. |
| **ThreadLocal** | A programming construct providing isolated variable instances restricted to the executing thread. |
| **WebSocket** | A network protocol (RFC 6455) providing persistent, bi-directional TCP communication channels over a single socket. |
