package com.healthcenter.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Creates SQLite connections with foreign key enforcement switched on. */
public final class DatabaseConnection {

    private static final String URL = "jdbc:sqlite:medicine_inventory.db";

    private DatabaseConnection() {
    }

    public static Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(URL);
        // SQLite ignores foreign keys unless this is enabled on EVERY connection
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON;");
        }
        return connection;
    }
}
