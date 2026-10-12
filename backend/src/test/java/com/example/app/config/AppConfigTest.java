package com.example.app.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AppConfigTest {

    private static Map<String, String> requiredEnv() {
        Map<String, String> env = new HashMap<>();
        env.put("APP_TLS_CERT_PATH", "/certs/localhost.pem");
        env.put("APP_TLS_KEY_PATH", "/certs/localhost-key.pem");
        env.put("APP_DB_URL", "jdbc:postgresql://localhost:5432/app");
        env.put("APP_DB_USER", "app_user");
        env.put("APP_DB_PASSWORD", "app-password");
        env.put("APP_DB_MIGRATION_USER", "app_owner");
        env.put("APP_DB_MIGRATION_PASSWORD", "owner-password");
        return env;
    }

    @Test
    void 既定値は_HTTPS_の_8443_と_接続の上限_1000_ミリ秒になる() {
        AppConfig config = AppConfig.fromEnv(requiredEnv());

        assertEquals(8443, config.httpsPort());
        assertEquals("/certs/localhost.pem", config.tlsCertPath());
        assertEquals("/certs/localhost-key.pem", config.tlsKeyPath());
        assertEquals(20, config.poolSize());
        assertEquals(1000, config.dbConnectionTimeoutMs());
    }

    @Test
    void 証明書と秘密鍵の設定が無いと_起動を中止し_値を含めない() {
        for (String name : new String[] {"APP_TLS_CERT_PATH", "APP_TLS_KEY_PATH"}) {
            Map<String, String> env = requiredEnv();
            env.remove(name);

            IllegalStateException e = assertThrows(IllegalStateException.class, () -> AppConfig.fromEnv(env));
            assertEquals("必須の環境変数が設定されていません: " + name, e.getMessage());
        }
    }

    @Test
    void 接続の上限は_1000_ミリ秒を超えると_1000_ミリ秒に丸める() {
        assertEquals(500, timeoutOf("500"));
        assertEquals(999, timeoutOf("999"));
        assertEquals(1000, timeoutOf("1000"));
        assertEquals(1000, timeoutOf("1001"));
        assertEquals(1000, timeoutOf("30000"));
    }

    @Test
    void 接続の上限が数値でない場合は_起動を中止する() {
        Map<String, String> env = requiredEnv();
        env.put("APP_DB_CONNECTION_TIMEOUT_MS", "abc");

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> AppConfig.fromEnv(env));
        assertFalse(e.getMessage().contains("abc"));
    }

    private static long timeoutOf(String value) {
        Map<String, String> env = requiredEnv();
        env.put("APP_DB_CONNECTION_TIMEOUT_MS", value);
        return AppConfig.fromEnv(env).dbConnectionTimeoutMs();
    }
}
