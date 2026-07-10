# Entity Reference

This document catalogs the JPA entities, properties, relations, lifecycles, and validation rules for the Chat Platform.

---

## 1. User Entity
- **Purpose**: Represents platform users.
- **Properties**: `id` (UUID), `username` (VARCHAR(30)), `email` (VARCHAR(255)), `passwordHash` (VARCHAR(255)), `createdAt` (Timestamp), `updatedAt` (Timestamp).
- **Validation**: Enforced via JSR-380 validation annotations on DTOs.
- **Lifecycle**: Handled by JPA auditing listener (`AuditingEntityListener.class`).

---

## 2. RefreshToken Entity
- **Purpose**: Maps long-lived session keys for JWT generation.
- **Properties**: `id` (UUID), `user` (Lazy User), `token` (VARCHAR(255) UK), `expiryDate` (Timestamp), `revoked` (boolean).
- **Lifecycle**: Transient -> Managed -> Removed (on logout or expiration).

---

## 3. Room Entity
- **Purpose**: Represents a chat channel created by users.
- **Properties**:
  - `id`: UUID Primary Key, auto-generated.
  - `name`: VARCHAR(50) UNIQUE, NOT NULL. Must match alphanumeric-hyphen rules.
  - `description`: VARCHAR(255) NULLABLE.
  - `roomType`: Enum (`RoomType`), stored as String (`@Enumerated(EnumType.STRING)`), NOT NULL. Values: `PUBLIC_GROUP`, `DIRECT_MESSAGE`.
  - `createdBy`: User entity reference. Configured as `LAZY` to avoid overhead during room listing.
  - `createdAt`: Timestamp, managed by JPA auditing.
- **Validation**: 
  - `name` must be between 3 and 50 characters, restricted to `^[a-zA-Z0-9_-]+$` (for groups) or `^dm:[a-fA-F0-9-]{36}:[a-fA-F0-9-]{36}$` (for direct messages).
  - `roomType` must not be null.

---

## 4. RoomMember Entity
- **Purpose**: Maps the many-to-many relationship between `User` and `Room` (membership).
- **Properties**:
  - `id`: Composite PK `RoomMemberId` containing `roomId` (UUID) and `userId` (UUID).
  - `room`: Lazy `Room` entity reference.
  - `user`: Lazy `User` entity reference.
  - `lastReadMessage`: Lazy `Message` entity reference, nullable, maps the user's current read marker.
  - `joinedAt`: Timestamp, mapped by `@CreatedDate`.
- **Validation**: Enforces unique primary key combinations to prevent duplicate membership records.

---

## 5. Message Entity
- **Purpose**: Represents a text message sent within a room, optionally containing file attachments.
- **Properties**:
  - `id`: UUID Primary Key, auto-generated.
  - `room`: Lazy `Room` reference (Cascade deletion when room is deleted).
  - `sender`: Lazy `User` reference (Mapped with `ON DELETE SET NULL` to preserve message logs if user is deleted).
  - `content`: TEXT column, nullable (if attachments are present).
  - `attachments`: One-to-Many relationship (`List<MessageAttachment>`), eagerly loaded or fetched as needed, with cascade operations enabled.
  - `createdAt`: Timestamp, managed by JPA auditing.
- **Validation**: `content` must be between 1 and 4000 characters if no attachments are present.

---

## 6. MessageAttachment Entity
- **Purpose**: Represents files or images attached to a message.
- **Properties**:
  - `id`: UUID Primary Key, auto-generated.
  - `message`: Lazy `Message` parent reference.
  - `fileName`: VARCHAR(255) file label.
  - `fileUrl`: VARCHAR(512) file download url.
  - `fileType`: VARCHAR(100) Mime-type classification (e.g. image/png).
  - `fileSize`: BIGINT value.
  - `createdAt`: Timestamp.
- **Validation**: `fileUrl` and `fileName` must not be blank. `fileSize` must be greater than 0.
