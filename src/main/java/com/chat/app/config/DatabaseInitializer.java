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
    }
}
