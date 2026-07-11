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
    }
}
