package com.example.app.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.app.testing.HttpsTestServer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AppConfigTest {

    private static Map<String, String> requiredEnvironment() {
        return new HashMap<>(HttpsTestServer.requiredEnvironment());
    }

    @Test
    void readsRequiredValuesAndAppliesDefaults() {
        AppConfig config = AppConfig.fromEnvironment(requiredEnvironment());

        assertEquals(8443, config.httpsPort());
        assertEquals(Path.of("/tmp/cert.pem"), config.tlsCertPath());
        assertEquals(Path.of("/tmp/key.pem"), config.tlsKeyPath());
        assertEquals("jdbc:postgresql://localhost:5432/app", config.dbUrl());
        assertEquals("app_user", config.dbUser());
        assertEquals("app-user-password", config.dbPassword());
        assertEquals("app_owner", config.dbMigrationUser());
        assertEquals("app-owner-password", config.dbMigrationPassword());
        assertEquals(20, config.dbPoolSize());
        assertEquals(Path.of("../frontend/dist"), config.staticDir());
    }

    @Test
    void readsOptionalValues() {
        Map<String, String> env = requiredEnvironment();
        env.put("APP_HTTPS_PORT", "9443");
        env.put("APP_DB_POOL_SIZE", "5");
        env.put("APP_STATIC_DIR", "/srv/static");

        AppConfig config = AppConfig.fromEnvironment(env);

        assertEquals(9443, config.httpsPort());
        assertEquals(5, config.dbPoolSize());
        assertEquals(Path.of("/srv/static"), config.staticDir());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "APP_TLS_CERT_PATH",
            "APP_TLS_KEY_PATH",
            "APP_DB_URL",
            "APP_DB_USER",
            "APP_DB_PASSWORD",
            "APP_DB_MIGRATION_USER",
            "APP_DB_MIGRATION_PASSWORD"})
    void abortsWhenRequiredValueIsMissing(String name) {
        Map<String, String> env = requiredEnvironment();
        env.remove(name);

        AppConfig.ConfigException e =
                assertThrows(AppConfig.ConfigException.class, () -> AppConfig.fromEnvironment(env));
        assertTrue(e.getMessage().contains(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"APP_TLS_CERT_PATH", "APP_DB_PASSWORD"})
    void abortsWhenRequiredValueIsBlank(String name) {
        Map<String, String> env = requiredEnvironment();
        env.put(name, " ");

        assertThrows(AppConfig.ConfigException.class, () -> AppConfig.fromEnvironment(env));
    }

    @Test
    void abortsWhenNothingIsSet() {
        AppConfig.ConfigException e =
                assertThrows(AppConfig.ConfigException.class, () -> AppConfig.fromEnvironment(Map.of()));
        assertTrue(e.getMessage().contains("APP_TLS_CERT_PATH"));
        assertTrue(e.getMessage().contains("APP_DB_MIGRATION_PASSWORD"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "65536"})
    void abortsWhenPortIsInvalid(String port) {
        Map<String, String> env = requiredEnvironment();
        env.put("APP_HTTPS_PORT", port);

        assertThrows(AppConfig.ConfigException.class, () -> AppConfig.fromEnvironment(env));
    }

    @Test
    void toStringDoesNotContainPasswords() {
        String text = AppConfig.fromEnvironment(requiredEnvironment()).toString();

        assertFalse(text.contains("app-user-password"));
        assertFalse(text.contains("app-owner-password"));
    }
}
