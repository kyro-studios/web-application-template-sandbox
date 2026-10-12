package com.example.app.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.app.config.AppConfig;
import com.example.app.testsupport.PostgresTestSupport;
import java.util.Map;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;

class DatabaseTest {

    @Test
    void マイグレーションはマイグレーション用の利用者で実行され_履歴の表の所有者になる() {
        AppConfig config = AppConfig.fromEnv(PostgresTestSupport.env());

        try (Database database = new Database(config)) {
            String owner = PostgresTestSupport.ownerJdbi().withHandle(handle -> handle.createQuery(
                            "SELECT tableowner FROM pg_tables WHERE schemaname = 'public' AND tablename = 'flyway_schema_history'")
                    .mapTo(String.class)
                    .one());

            assertEquals(PostgresTestSupport.OWNER_USER, owner);
        }
    }

    @Test
    void プールはアプリケーション用の利用者で接続する() {
        AppConfig config = AppConfig.fromEnv(PostgresTestSupport.env());

        try (Database database = new Database(config)) {
            String user = database.jdbi().withHandle(handle -> handle.createQuery("SELECT current_user")
                    .mapTo(String.class)
                    .one());

            assertEquals(PostgresTestSupport.APP_USER, user);
        }
    }

    @Test
    void 接続の時間の上限は既定の1000ミリ秒で_プールの大きさは既定の20になる() {
        AppConfig config = AppConfig.fromEnv(PostgresTestSupport.env());

        try (Database database = new Database(config)) {
            assertEquals(1000, database.dataSource().getConnectionTimeout());
            assertEquals(20, database.dataSource().getMaximumPoolSize());
        }
    }

    @Test
    void 接続の時間の上限とプールの大きさは設定の値を使う() {
        Map<String, String> env = PostgresTestSupport.env();
        env.put("APP_DB_CONNECTION_TIMEOUT_MS", "500");
        env.put("APP_DB_POOL_SIZE", "3");
        AppConfig config = AppConfig.fromEnv(env);

        try (Database database = new Database(config)) {
            assertEquals(500, database.dataSource().getConnectionTimeout());
            assertEquals(3, database.dataSource().getMaximumPoolSize());
        }
    }

    @Test
    void マイグレーション用の利用者で接続できない場合は例外になる() {
        Map<String, String> env = PostgresTestSupport.env();
        env.put("APP_DB_MIGRATION_PASSWORD", "wrong-password");
        AppConfig config = AppConfig.fromEnv(env);

        assertThrows(FlywayException.class, () -> new Database(config));
    }

    @Test
    void 閉じるとコネクションプールも閉じる() {
        AppConfig config = AppConfig.fromEnv(PostgresTestSupport.env());
        Database database = new Database(config);

        database.close();

        assertTrue(database.dataSource().isClosed());
    }
}
