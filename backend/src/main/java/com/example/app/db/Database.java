package com.example.app.db;

import com.example.app.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import java.util.Objects;
import org.flywaydb.core.Flyway;
import org.jdbi.v3.core.Jdbi;

/**
 * データベースの土台。起動時に、マイグレーション用の利用者で Flyway を実行し、
 * アプリケーション用の利用者で HikariCP のコネクションプールと Jdbi を作る (認証機能の設計 7.6)。
 */
public final class Database implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final Jdbi jdbi;

    public Database(AppConfig config) {
        Objects.requireNonNull(config);
        migrate(config);

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.dbUrl());
        hikari.setUsername(config.dbUser());
        hikari.setPassword(config.dbPassword());
        hikari.setMaximumPoolSize(config.poolSize());
        hikari.setConnectionTimeout(config.dbConnectionTimeoutMs());
        this.dataSource = new HikariDataSource(hikari);
        this.jdbi = Jdbi.create(dataSource);
    }

    /** マイグレーションの接続は、コネクションプールに入れず、終わったら閉じる */
    private static void migrate(AppConfig config) {
        Flyway.configure()
                .dataSource(config.dbUrl(), config.migrationUser(), config.migrationPassword())
                .locations("classpath:db/migration")
                // 認証機能の版 (V1) と、お知らせの版 (35.1) の、どちらが先に適用されても良いようにする (アーキテクチャ設計 10 章)
                .outOfOrder(true)
                .placeholders(Map.of("app_db_user", config.dbUser()))
                .load()
                .migrate();
    }

    public Jdbi jdbi() {
        return jdbi;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
