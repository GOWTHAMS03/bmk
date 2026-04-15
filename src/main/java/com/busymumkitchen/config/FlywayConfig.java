package com.busymumkitchen.config;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway configuration that runs repair before migrate.
 * This ensures any checksum mismatches (from edited migration files)
 * are automatically resolved by updating the flyway_schema_history table.
 */
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            // Repair updates checksums in flyway_schema_history to match current files
            flyway.repair();
            // Then run the actual migration
            flyway.migrate();
        };
    }
}

