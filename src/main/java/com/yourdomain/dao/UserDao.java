package com.yourdomain.dao;

import com.yourdomain.model.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** All SQL for the users table. Account events are audit-logged from here. */
public class UserDao {

    private final ActivityLogDao logDao = new ActivityLogDao();

    public static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Self-registration. New accounts start as PENDING until an admin approves them. */
    public int registerUser(String username, String password, String fullName, String role) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash, full_name, role, status) "
                + "VALUES (?, ?, ?, ?, 'PENDING')";
        int newId;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, hashPassword(password));
            ps.setString(3, fullName);
            ps.setString(4, role);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                newId = keys.next() ? keys.getInt(1) : -1;
            }
        }
        if (newId > 0) {
            logDao.logAction(newId, "USER_REGISTERED",
                    "User " + username + " (ID: " + newId + ") registered as " + role + " - pending approval.");
        }
        return newId;
    }

    public boolean usernameExists(String username) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Returns the user if username + password match, otherwise empty. */
    public Optional<User> authenticate(String username, String password) throws SQLException {
        String sql = "SELECT id, username, full_name, role, status FROM users "
                + "WHERE username = ? AND password_hash = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, hashPassword(password));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    public Optional<User> findById(int userId) throws SQLException {
        String sql = "SELECT id, username, full_name, role, status FROM users WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    public List<User> getPendingUsers() throws SQLException {
        return query("SELECT id, username, full_name, role, status FROM users WHERE status = 'PENDING' ORDER BY id");
    }

    public List<User> getAllUsers() throws SQLException {
        return query("SELECT id, username, full_name, role, status FROM users ORDER BY id");
    }

    /** Account approval (Deliverable 5) -> logs USER_APPROVED. */
    public boolean approveUser(int adminId, int userId) throws SQLException {
        String sql = "UPDATE users SET status = 'ACTIVE' WHERE id = ? AND status = 'PENDING'";
        int rows;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            rows = ps.executeUpdate();
        }
        if (rows > 0) {
            logDao.logAction(adminId, "USER_APPROVED",
                    "Admin " + adminId + " approved account for User ID " + userId);
            return true;
        }
        return false;
    }

    /**
     * Role / status edit (Deliverable 7) -> logs USER_UPDATED.
     * Pass null for newRole or newStatus to keep the current value.
     */
    public boolean updateUserRoleAndStatus(int adminId, int userId, String newRole, String newStatus)
            throws SQLException {
        Optional<User> existing = findById(userId);
        if (existing.isEmpty()) {
            return false;
        }
        User old = existing.get();
        String role = (newRole == null) ? old.role() : newRole;
        String status = (newStatus == null) ? old.status() : newStatus;

        List<String> changes = new ArrayList<>();
        if (!role.equals(old.role())) {
            changes.add("changed role of User " + userId + " to " + role);
        }
        if (!status.equals(old.status())) {
            changes.add("changed status of User " + userId + " to " + status);
        }
        if (changes.isEmpty()) {
            return false; // nothing to change, nothing to log
        }

        String sql = "UPDATE users SET role = ?, status = ? WHERE id = ?";
        int rows;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, role);
            ps.setString(2, status);
            ps.setInt(3, userId);
            rows = ps.executeUpdate();
        }
        if (rows > 0) {
            logDao.logAction(adminId, "USER_UPDATED", "Admin " + adminId + " " + String.join("; ", changes));
            return true;
        }
        return false;
    }

    private List<User> query(String sql) throws SQLException {
        List<User> users = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(mapRow(rs));
            }
        }
        return users;
    }

    private User mapRow(ResultSet rs) throws SQLException {
        return new User(rs.getInt("id"), rs.getString("username"), rs.getString("full_name"),
                rs.getString("role"), rs.getString("status"));
    }
}
