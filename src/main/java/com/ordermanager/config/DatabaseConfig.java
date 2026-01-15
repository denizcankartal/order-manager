package com.ordermanager.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jdbi.v3.core.Jdbi;

import javax.sql.DataSource;

public class DatabaseConfig {

    private final DataSource dataSource;
    private final Jdbi jdbi;

    public DatabaseConfig(AppConfig appConfig) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(System.getenv().getOrDefault("DB_URL",
                "jdbc:postgresql://localhost:5432/order_manager"));
        cfg.setUsername(System.getenv().getOrDefault("DB_USER", "order_user"));
        cfg.setPassword(System.getenv().getOrDefault("DB_PASSWORD", "order_pass"));
        cfg.setMaximumPoolSize(5);

        this.dataSource = new HikariDataSource(cfg);
        this.jdbi = Jdbi.create(dataSource);
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public Jdbi getJdbi() {
        return jdbi;
    }
}
