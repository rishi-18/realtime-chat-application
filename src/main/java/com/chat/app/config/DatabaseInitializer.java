package com.chat.app.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        log.info("Initializing custom database indexes...");
        try {
            jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_messages_content_fts " +
                "ON messages USING gin(to_tsvector('english', coalesce(content, ''))) " +
                "WHERE is_deleted = false"
            );
            log.info("Successfully validated/created idx_messages_content_fts GIN index.");
        } catch (Exception e) {
            log.warn("Could not create GIN index idx_messages_content_fts. This may happen if the database dialect is not PostgreSQL. Error: {}", e.getMessage());
        }

        log.info("Checking room member role column and migrating ownership data...");
        try {
            jdbcTemplate.execute("ALTER TABLE room_members ADD COLUMN IF NOT EXISTS role VARCHAR(30) DEFAULT 'MEMBER'");
            
            // Set role to 'OWNER' for room creators
            jdbcTemplate.execute(
                "UPDATE room_members rm " +
                "SET role = 'OWNER' " +
                "FROM rooms r " +
                "WHERE rm.room_id = r.id AND rm.user_id = r.created_by_user_id"
            );
            log.info("Room member role schema and ownership values migrated successfully.");
        } catch (Exception e) {
            log.warn("Could not run room member role migrations. Error: {}", e.getMessage());
        }

        log.info("Checking messages parent_message_id column and creating indexing...");
        try {
            jdbcTemplate.execute("ALTER TABLE messages ADD COLUMN IF NOT EXISTS parent_message_id UUID REFERENCES messages(id) ON DELETE CASCADE");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_messages_parent ON messages(parent_message_id)");
            log.info("Messages parent_message_id schema and indexing configured successfully.");
        } catch (Exception e) {
            log.warn("Could not run message threading schema migrations. Error: {}", e.getMessage());
        }

        log.info("Checking message_revisions table and creating indexing...");
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS message_revisions (id UUID PRIMARY KEY, message_id UUID REFERENCES messages(id) ON DELETE CASCADE, old_content TEXT NOT NULL, edited_at TIMESTAMP NOT NULL)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_revisions_message ON message_revisions(message_id)");
            log.info("Message revisions table schema and indexing configured successfully.");
        } catch (Exception e) {
            log.warn("Could not run message revisions schema migrations. Error: {}", e.getMessage());
        }

        log.info("Checking room_invites table and creating indexing...");
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS room_invites (id UUID PRIMARY KEY, room_id UUID REFERENCES rooms(id) ON DELETE CASCADE, code VARCHAR(50) UNIQUE NOT NULL, created_by UUID REFERENCES users(id) ON DELETE SET NULL, max_uses INTEGER, uses INTEGER NOT NULL DEFAULT 0, expires_at TIMESTAMP, created_at TIMESTAMP NOT NULL)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_invites_code ON room_invites(code)");
            log.info("Room invites table schema and indexing configured successfully.");
        } catch (Exception e) {
            log.warn("Could not run room invites schema migrations. Error: {}", e.getMessage());
        }

        log.info("Checking user_blocks table and creating indexing...");
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS user_blocks (id UUID PRIMARY KEY, user_id UUID REFERENCES users(id) ON DELETE CASCADE, blocked_user_id UUID REFERENCES users(id) ON DELETE CASCADE, created_at TIMESTAMP NOT NULL, UNIQUE(user_id, blocked_user_id))");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_user_blocks_user ON user_blocks(user_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_user_blocks_blocked ON user_blocks(blocked_user_id)");
            log.info("User blocks table schema and indexing configured successfully.");
        } catch (Exception e) {
            log.warn("Could not run user blocks schema migrations. Error: {}", e.getMessage());
        }
    }
}
