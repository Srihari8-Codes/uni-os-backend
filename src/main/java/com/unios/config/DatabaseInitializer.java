package com.unios.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * DatabaseInitializer — Auto-seeds the database from the bundled SQL dump
 * on the FIRST deployment to a new machine.
 *
 * HOW TO USE:
 *  - On first deployment: set app.db.auto-seed=true in application.properties (or env var DB_AUTO_SEED=true)
 *  - After the first successful boot, set it back to false (default) so it never runs again.
 *  - It is safe to leave enabled — it checks if data already exists before seeding.
 */
@Configuration
@Slf4j
public class DatabaseInitializer {

    private final JdbcTemplate jdbcTemplate;

    @Value("${APP_DB_AUTO_SEED:never}")
    private String autoSeed;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(1) // Run BEFORE GoalEngineExampleConfig (which is Order 2)
    public void seedDatabaseIfEmpty() {
        if (!"always".equalsIgnoreCase(autoSeed)) {
            log.info("[DB-INIT] Auto-seed is not set to 'always'. Skipping.");
            return;
        }

        try {
            // Check if the database already has university data
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM universities", Integer.class);

            if (count != null && count > 0) {
                log.info("[DB-INIT] Database already has {} university records. Skipping seed.", count);
                return;
            }
        } catch (Exception e) {
            log.warn("[DB-INIT] Could not check existing data (table may not exist yet): {}", e.getMessage());
            // If the table doesn't exist, proceed with seeding
        }

        log.info("[DB-INIT] *** Database appears empty. Running seed from unios_dump.sql ***");
        try {
            ClassPathResource resource = new ClassPathResource("db/unios_dump.sql");
            if (!resource.exists()) {
                log.error("[DB-INIT] ERROR: db/unios_dump.sql not found in classpath! " +
                          "Run pg_dump and place the file in src/main/resources/db/");
                return;
            }

            String sql;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                sql = reader.lines().collect(Collectors.joining("\n"));
            }

            // Split by semicolons and execute each statement
            String[] statements = sql.split(";");
            int executed = 0;
            int skipped = 0;

            for (String statement : statements) {
                String trimmed = statement.trim();
                // Skip empty statements and comments
                if (trimmed.isEmpty() || trimmed.startsWith("--") || trimmed.startsWith("/*")) {
                    skipped++;
                    continue;
                }
                try {
                    jdbcTemplate.execute(trimmed);
                    executed++;
                } catch (Exception e) {
                    // Log but continue — some statements may already exist
                    log.debug("[DB-INIT] Skipping statement (may already exist): {}", e.getMessage());
                    skipped++;
                }
            }

            log.info("[DB-INIT] *** Seed complete. Executed: {}, Skipped/Existing: {} ***", executed, skipped);
            log.info("[DB-INIT] IMPORTANT: Set app.db.auto-seed=false in application.properties now.");

        } catch (Exception e) {
            log.error("[DB-INIT] FATAL: Failed to seed database from dump: {}", e.getMessage(), e);
        }
    }
}
