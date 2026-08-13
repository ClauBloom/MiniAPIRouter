package com.miniapi.router.saas.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Flyway migration chain against a real MariaDB instance.
 * <p>Reads connection settings from the repository-root {@code .env} so no
 * credential is hard-coded. Excluded from the default unit suite; run with
 * {@code mvn -pl ai-router-saas -am -Pintegration test}.
 */
class FlywayMigrationIntegrationTest {

    @Test
    void migrationsApplyAndCreateBaselineTables() throws Exception {
        Map<String, String> env = readRootEnv();
        String url = env.get("SAAS_DB_URL");
        String username = env.get("SAAS_DB_USERNAME");
        String password = env.get("SAAS_DB_PASSWORD");
        assertThat(url).as("SAAS_DB_URL must be present in repository-root .env").isNotBlank();

        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();

        try (Connection connection = DriverManager.getConnection(url, username, password);
             ResultSet tenants = connection.getMetaData().getTables(null, null, "tenant", null);
             ResultSet refresh = connection.getMetaData().getTables(null, null, "refresh_session", null);
             ResultSet intents = connection.getMetaData().getTables(null, null, "intent_config", null);
             ResultSet settings = connection.getMetaData().getTables(null, null, "system_setting", null)) {
            assertThat(tenants.next()).as("tenant table exists").isTrue();
            assertThat(refresh.next()).as("refresh_session table exists").isTrue();
            assertThat(intents.next()).as("intent_config table exists").isTrue();
            assertThat(settings.next()).as("system_setting table exists").isTrue();
        }
    }

    private Map<String, String> readRootEnv() throws IOException {
        Path envFile = Path.of("..", ".env");
        Map<String, String> env = new HashMap<>();
        if (!Files.exists(envFile)) return env;
        for (String line : Files.readAllLines(envFile)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int separator = trimmed.indexOf('=');
            if (separator <= 0) continue;
            env.put(trimmed.substring(0, separator).trim(), trimmed.substring(separator + 1).trim());
        }
        return env;
    }
}
