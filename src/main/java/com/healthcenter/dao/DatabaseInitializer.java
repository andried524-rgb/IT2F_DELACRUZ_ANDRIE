package com.healthcenter.dao;

import com.healthcenter.model.Roles;
import com.healthcenter.model.Statuses;
import com.healthcenter.util.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Creates every required table (if missing) before the CLI menu starts. */
public final class DatabaseInitializer {

    private static final String CREATE_USERS = """
            CREATE TABLE IF NOT EXISTS users (
                user_id       INTEGER PRIMARY KEY AUTOINCREMENT,
                username      TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                full_name     TEXT NOT NULL,
                role          TEXT NOT NULL CHECK (role IN ('ADMIN', 'PHARMACY', 'NURSE')),
                status        TEXT NOT NULL DEFAULT 'PENDING'
                              CHECK (status IN ('PENDING', 'APPROVED', 'INACTIVE')),
                created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )""";

    private static final String CREATE_MEDICINES = """
            CREATE TABLE IF NOT EXISTS medicines (
                medicine_id   INTEGER PRIMARY KEY AUTOINCREMENT,
                generic_name  TEXT NOT NULL,
                brand_name    TEXT,
                category      TEXT,
                dosage_form   TEXT,
                strength      TEXT,
                unit          TEXT,
                reorder_level INTEGER NOT NULL DEFAULT 0,
                status        TEXT NOT NULL DEFAULT 'ACTIVE'
            )""";

    private static final String CREATE_STOCK_BATCHES = """
            CREATE TABLE IF NOT EXISTS stock_batches (
                batch_id           INTEGER PRIMARY KEY AUTOINCREMENT,
                medicine_id        INTEGER NOT NULL,
                batch_no           TEXT NOT NULL,
                quantity_received  INTEGER NOT NULL,
                quantity_remaining INTEGER NOT NULL,
                expiry_date        TEXT NOT NULL,
                date_received      TEXT NOT NULL,
                source             TEXT,
                status             TEXT NOT NULL DEFAULT 'ACTIVE',
                FOREIGN KEY (medicine_id) REFERENCES medicines (medicine_id)
            )""";

    private static final String CREATE_DISPENSING_RECORDS = """
            CREATE TABLE IF NOT EXISTS dispensing_records (
                dispense_id    INTEGER PRIMARY KEY AUTOINCREMENT,
                medicine_id    INTEGER NOT NULL,
                batch_id       INTEGER NOT NULL,
                patient_name   TEXT NOT NULL,
                patient_age    INTEGER,
                quantity       INTEGER NOT NULL,
                dispensed_by   INTEGER NOT NULL,
                date_dispensed TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (medicine_id)  REFERENCES medicines (medicine_id),
                FOREIGN KEY (batch_id)     REFERENCES stock_batches (batch_id),
                FOREIGN KEY (dispensed_by) REFERENCES users (user_id)
            )""";

    private static final String CREATE_AUDIT_LOGS = """
            CREATE TABLE IF NOT EXISTS audit_logs (
                log_id        INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id       INTEGER NOT NULL,
                action        TEXT NOT NULL,
                table_name    TEXT,
                record_id     INTEGER,
                old_value     TEXT,
                new_value     TEXT,
                log_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (user_id) REFERENCES users (user_id)
            )""";

    private DatabaseInitializer() {
    }

    public static void initialize() {
        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement()) {

            // Parent tables first, then tables that reference them
            statement.execute(CREATE_USERS);
            statement.execute(CREATE_MEDICINES);
            statement.execute(CREATE_STOCK_BATCHES);
            statement.execute(CREATE_DISPENSING_RECORDS);
            statement.execute(CREATE_AUDIT_LOGS);

            seedDefaultAdmin(connection);
            System.out.println("[OK] Database initialized: all tables are ready.");

        } catch (SQLException e) {
            throw new IllegalStateException("Database initialization failed: " + e.getMessage(), e);
        }
    }

    /** Makes sure at least one approved admin exists so accounts can be approved. */
    private static void seedDefaultAdmin(Connection connection) throws SQLException {
        String countSql = "SELECT COUNT(*) FROM users WHERE role = ?";
        try (PreparedStatement count = connection.prepareStatement(countSql)) {
            count.setString(1, Roles.ADMIN);
            try (ResultSet rs = count.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return;
                }
            }
        }
        String insertSql = "INSERT INTO users (username, password_hash, full_name, role, status) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
            insert.setString(1, "admin");
            insert.setString(2, PasswordUtil.hash("admin123"));
            insert.setString(3, "Default Administrator");
            insert.setString(4, Roles.ADMIN);
            insert.setString(5, Statuses.APPROVED);
            insert.executeUpdate();
            System.out.println("[OK] Default admin created -> username: admin | password: admin123");
        }
    }
}
