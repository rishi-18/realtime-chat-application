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
  - `name` must be between 3 and 50 characters. For group channels, it is restricted to `^[a-zA-Z0-9_-]+$`. For direct messages, it is auto-generated as `dm-{MD5(sorted_userIds)}` (exactly 35 characters) to fit within the 50-character schema limit.
  - `roomType` must not be null.

---

## 4. RoomMember Entity
- **Purpose**: Maps the many-to-many relationship between `User` and `Room` (membership).
- **Properties**:
  - `id`: Composite PK `RoomMemberId` containing `roomId` (UUID) and `userId` (UUID).
  - `room`: Lazy `Room` entity reference.
  - `user`: Lazy `User` entity reference.
  - `lastReadMessage`: Lazy `Message` entity reference, nullable, maps the user's current read marker.
  - `role`: Enum (`RoomRole`), stored as String (`@Enumerated(EnumType.STRING)`), NOT NULL. Values: `OWNER`, `MODERATOR`, `MEMBER`.
  - `joinedAt`: Timestamp, mapped by `@CreatedDate`.
- **Validation**: Enforces unique primary key combinations to prevent duplicate membership records. Role must not be null and must correspond to a valid enum classification.

---

## 5. Message Entity
- **Purpose**: Represents a text message sent within a room, optionally containing file attachments. Supports soft-deletes and tracking edits.
- **Properties**:
  - `id`: UUID Primary Key, auto-generated.
  - `room`: Lazy `Room` reference (Cascade deletion when room is deleted).
  - `sender`: Lazy `User` reference (Mapped with `ON DELETE SET NULL` to preserve message logs if user is deleted).
  - `parentMessage`: Lazy `Message` self-referencing entity reference (nullable, represents the parent thread message).
  - `replies`: One-to-Many self-referencing relationship representing all reply messages in the thread.
  - `content`: TEXT column, nullable (if attachments are present, or if message has been soft-deleted).
  - `isDeleted`: BOOLEAN column, defaults to false.
  - `attachments`: One-to-Many relationship (`List<MessageAttachment>`), eagerly loaded or fetched as needed, with cascade operations enabled.
  - `createdAt`: Timestamp, managed by JPA auditing.
  - `updatedAt`: Timestamp, updated when message is edited.
- **Validation**: `content` must be between 1 and 4000 characters if no attachments are present and the message is not deleted.
- **Indexing**: GIN (Generalized Inverted Index) is configured on the `content` field to support high-performance native full-text search.

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

---

## 7. MessageReaction Entity
- **Purpose**: Represents user emoji reactions placed on a message.
- **Properties**:
  - `id`: UUID Primary Key, auto-generated.
  - `message`: Lazy `Message` reference.
  - `user`: Lazy `User` reference.
  - `emoji`: VARCHAR(32) representing the emoji (Unicode representation or custom shortcode).
  - `createdAt`: Timestamp.
- **Validation**: `emoji` must not be blank and must represent a valid Unicode sequence. Composite uniqueness constraint prevents redundant reactions.

---

## 8. MessageMention Entity
- **Purpose**: Represents a user mention (`@username`) recorded inside a message.
- **Properties**:
  - `id`: UUID Primary Key, auto-generated.
  - `message`: Lazy `Message` reference.
  - `user`: Lazy `User` reference.
  - `createdAt`: Timestamp.
- **Validation**: Composite unique constraint ensures a user is not listed as mentioned multiple times on the same message. Only valid channel members can be mentioned.

---

## 9. PinnedMessage Entity
- **Purpose**: Represents a message pinned within a room.
- **Properties**:
  - `id`: UUID Primary Key, auto-generated.
  - `message`: Lazy `Message` reference.
  - `room`: Lazy `Room` reference.
  - `pinnedBy`: Lazy `User` reference.
  - `createdAt`: Timestamp.
- **Validation**: Target message must not be deleted. Senders must be members of the room where the message belongs. Composite unique constraint prevents duplicate pins of a message in a room.

---

## 10. MessageRevision Entity
- **Purpose**: Represents an archived revision log of modified message contents.
- **Properties**:
  - `id`: UUID Primary Key, auto-generated.
  - `message`: Lazy `Message` reference (Cascade deletes all logs if the message is deleted).
  - `oldContent`: TEXT column containing the message content prior to edit.
  - `editedAt`: Timestamp.
- **Validation**: `oldContent` must not be blank and conforms to character limit bounds. Indexing on `message_id` optimizes historical lookup times.
