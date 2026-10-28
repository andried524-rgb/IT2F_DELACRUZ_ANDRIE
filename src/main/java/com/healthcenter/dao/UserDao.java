package com.healthcenter.dao;

import com.healthcenter.model.Statuses;
import com.healthcenter.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** All SQL for the users table lives here. Every query uses a PreparedStatement. */
public class UserDao {

    private static final String COLUMNS = "user_id, username, full_name, role, status";

    // ---------------------------------------------------------- Activity 1

    public boolean usernameExists(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Database error (usernameExists): " + e.getMessage());
            return false;
        }
    }

    /** Inserts a new account. Every new account starts as PENDING. */
    public boolean registerUser(String username, String passwordHash, String fullName, String role) {
        String sql = "INSERT INTO users (username, password_hash, full_name, role, status) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, fullName);
            ps.setString(4, role.toUpperCase());
            ps.setString(5, Statuses.PENDING);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            System.err.println("Database error (registerUser): " + e.getMessage());
            return false;
        }
    }

    /** Returns the matching user (any status) or null when the credentials are wrong. */
    public User authenticate(String username, String passwordHash) {
        String sql = "SELECT " + COLUMNS + " FROM users WHERE username = ? AND password_hash = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            System.err.println("Database error (authenticate): " + e.getMessage());
            return null;
        }
    }

    // ---------------------------------------------------------- Activity 2

    /** All registered accounts, regardless of role or status. */
    public List<User> getAllUsers() {
        String sql = "SELECT " + COLUMNS + " FROM users ORDER BY user_id";
        return queryUsers(sql, null);
    }

    /** Accounts waiting for approval, across all roles. */
    public List<User> getPendingUsers() {
        String sql = "SELECT " + COLUMNS + " FROM users WHERE status = ? ORDER BY user_id";
        return queryUsers(sql, Statuses.PENDING);
    }

    public boolean approveUser(int userId) {
        String sql = "UPDATE users SET status = 'APPROVED' WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            System.err.println("Database error (approveUser): " + e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------ helpers

    private List<User> queryUsers(String sql, String param) {
        List<User> users = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (param != null) {
                ps.setString(1, param);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error (queryUsers): " + e.getMessage());
        }
        return users;
    }

    private User mapRow(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("user_id"),
                rs.getString("username"),
                rs.getString("full_name"),
                rs.getString("role"),
                rs.getString("status"));
    }
}
