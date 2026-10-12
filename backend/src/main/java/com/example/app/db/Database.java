package com.example.app.db;

import com.example.app.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.postgres.PostgresPlugin;

/**
 * データベースへの接続を組み立てる。
 *
 * <p>起動時に、マイグレーション用の利用者 (APP_DB_MIGRATION_USER) で Flyway を実行する。この接続はコネクションプールに入れず、
 * マイグレーションが終わったら閉じる。その後に、アプリケーション用の利用者 (APP_DB_USER) で HikariCP のコネクションプールを作り、
 * Jdbi を組み立てる (認証機能の設計 7.6)。マイグレーションに失敗した場合は {@link org.flywaydb.core.api.FlywayException} を投げ、
 * 起動を中止する。
 */
public final class Database implements AutoCloseable {

    private static final String MIGRATION_LOCATION = "classpath:db/migration";

    private final HikariDataSource dataSource;
    private final Jdbi jdbi;

    /**
     * @throws org.flywaydb.core.api.FlywayException マイグレーションに失敗した場合 (起動を中止する)
     */
    public Database(AppConfig config) {
        migrate(config);
        this.dataSource = dataSource(config);
        this.jdbi = Jdbi.create(dataSource).installPlugin(new PostgresPlugin());
    }

    /** アプリケーション用の利用者で接続する Jdbi。要求の処理では、これを使う */
    public Jdbi jdbi() {
        return jdbi;
    }

    @Override
    public void close() {
        dataSource.close();
    }

    HikariDataSource dataSource() {
        return dataSource;
    }

    private static void migrate(AppConfig config) {
        // マイグレーションの SQL の ${app_db_user} に、アプリケーション用の利用者の名前を入れる (GRANT で使う)
        Flyway.configure()
                .dataSource(config.dbUrl(), config.dbMigrationUser(), config.dbMigrationPassword())
                .locations(MIGRATION_LOCATION)
                .placeholders(Map.of("app_db_user", config.dbUser()))
                // お知らせの V35_1 と認証機能の V1 の適用順の食い違いを許す (お知らせの一覧の設計 10 章)
                .outOfOrder(true)
                .load()
                .migrate();
    }

    private static HikariDataSource dataSource(AppConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.dbUrl());
        hikari.setUsername(config.dbUser());
        hikari.setPassword(config.dbPassword());
        hikari.setMaximumPoolSize(config.dbPoolSize());
        hikari.setConnectionTimeout(config.dbConnectionTimeoutMs());
        return new HikariDataSource(hikari);
    }
}
