package com.yourdomain.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Creates all tables on startup (only if they do not exist yet). */
public final class DatabaseInitializer {

    private DatabaseInitializer() {
    }

    private static final String CREATE_USERS = """
            CREATE TABLE IF NOT EXISTS users (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                username      TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                full_name     TEXT NOT NULL,
                role          TEXT NOT NULL CHECK (role IN ('ADMIN','PHARMACY_STAFF','NURSE')),
                status        TEXT NOT NULL DEFAULT 'PENDING'
                              CHECK (status IN ('PENDING','ACTIVE','INACTIVE')),
                created_at    TEXT NOT NULL DEFAULT (datetime('now','localtime'))
            )""";

    private static final String CREATE_MEDICINES = """
            CREATE TABLE IF NOT EXISTS medicines (
                medicine_id   INTEGER PRIMARY KEY AUTOINCREMENT,
                generic_name  TEXT NOT NULL,
                brand_name    TEXT,
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
                date_received      TEXT NOT NULL DEFAULT (date('now','localtime')),
                source             TEXT,
                status             TEXT NOT NULL DEFAULT 'ACTIVE',
                disposal_reason    TEXT,
                FOREIGN KEY (medicine_id) REFERENCES medicines(medicine_id)
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
                date_dispensed TEXT NOT NULL DEFAULT (datetime('now','localtime')),
                FOREIGN KEY (medicine_id)  REFERENCES medicines(medicine_id),
                FOREIGN KEY (batch_id)     REFERENCES stock_batches(batch_id),
                FOREIGN KEY (dispensed_by) REFERENCES users(id)
            )""";

    /** Deliverable 8 table: audit trail of every critical action. */
    private static final String CREATE_ACTIVITY_LOGS = """
            CREATE TABLE IF NOT EXISTS activity_logs (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id     INTEGER NOT NULL,
                action_type TEXT NOT NULL,
                description TEXT NOT NULL,
                timestamp   TEXT NOT NULL DEFAULT (datetime('now','localtime')),
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )""";

    public static void initialize() {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement()) {
            st.execute(CREATE_USERS);
            st.execute(CREATE_MEDICINES);
            st.execute(CREATE_STOCK_BATCHES);
            st.execute(CREATE_DISPENSING_RECORDS);
            st.execute(CREATE_ACTIVITY_LOGS);
            seedDefaultAdmin(conn);
        } catch (SQLException e) {
            System.err.println("Fatal: could not initialize database - " + e.getMessage());
            System.exit(1);
        }
    }

    /** Creates a first administrator so you can log in on a brand-new database. */
    private static void seedDefaultAdmin(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
            if (rs.next() && rs.getInt(1) > 0) {
                return;
            }
        }
        String sql = "INSERT INTO users (username, password_hash, full_name, role, status) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "admin");
            ps.setString(2, UserDao.hashPassword("admin123"));
            ps.setString(3, "Health Center Administrator");
            ps.setString(4, "ADMIN");
            ps.setString(5, "ACTIVE");
            ps.executeUpdate();
        }
        System.out.println("[Setup] Default administrator created -> username: admin | password: admin123");
    }
}
