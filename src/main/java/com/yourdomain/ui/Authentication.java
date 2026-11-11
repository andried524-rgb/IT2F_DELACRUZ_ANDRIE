package com.yourdomain.ui;

import com.yourdomain.dao.ActivityLogDao;
import com.yourdomain.dao.UserDao;
import com.yourdomain.model.User;

import java.util.Optional;

import static com.yourdomain.ui.ConsoleInput.readIntInRange;
import static com.yourdomain.ui.ConsoleInput.readLine;
import static com.yourdomain.ui.ConsoleInput.readNonEmpty;
import static com.yourdomain.ui.ConsoleInput.safely;

/** Login / logout / registration. Login and logout are audit-logged here. */
public final class Authentication {

    private static final UserDao USER_DAO = new UserDao();
    private static final ActivityLogDao LOG_DAO = new ActivityLogDao();

    private Authentication() {
    }

    /** Main entry loop of the application. */
    public static void start() {
        while (true) {
            System.out.println("\n=== HEALTH CENTER MEDICINE INVENTORY SYSTEM ===");
            System.out.println("1. Login");
            System.out.println("2. Register new account");
            System.out.println("0. Exit");
            int choice = readIntInRange("Choose: ", 0, 2);

            switch (choice) {
                case 1 -> {
                    User user = login();
                    if (user != null) {
                        openMenuFor(user);
                        logout(user);
                    }
                }
                case 2 -> safely(Authentication::register);
                default -> {
                    System.out.println("Goodbye!");
                    return;
                }
            }
        }
    }

    private static void openMenuFor(User user) {
        if ("ADMIN".equals(user.role())) {
            AdminMenu.show(user);
        } else {
            StaffMenu.show(user);
        }
    }

    /** Returns the logged-in user, or null if login failed. Logs USER_LOGIN. */
    public static User login() {
        System.out.println("\n--- LOGIN ---");
        String username = readNonEmpty("Username: ");
        String password = readNonEmpty("Password: ");
        try {
            Optional<User> found = USER_DAO.authenticate(username, password);
            if (found.isEmpty()) {
                System.out.println("  Invalid username or password.");
                return null;
            }
            User user = found.get();
            switch (user.status()) {
                case "PENDING" -> {
                    System.out.println("  Your account is still pending administrator approval.");
                    return null;
                }
                case "INACTIVE" -> {
                    System.out.println("  Your account has been deactivated. Contact the administrator.");
                    return null;
                }
                default -> {
                }
            }
            LOG_DAO.logAction(user.id(), "USER_LOGIN",
                    "User " + user.username() + " (ID: " + user.id() + ") logged in.");
            System.out.println("  Welcome, " + user.fullName() + " [" + user.role() + "]");
            return user;
        } catch (java.sql.SQLException e) {
            System.out.println("  [Database error] " + e.getMessage());
            return null;
        }
    }

    /** Logs USER_LOGOUT. */
    public static void logout(User user) {
        LOG_DAO.logAction(user.id(), "USER_LOGOUT",
                "User " + user.username() + " (ID: " + user.id() + ") logged out.");
        System.out.println("  You have been logged out.");
    }

    private static void register() throws java.sql.SQLException {
        System.out.println("\n--- REGISTER ---");
        String username = readNonEmpty("Choose a username: ");
        if (USER_DAO.usernameExists(username)) {
            System.out.println("  That username is already taken.");
            return;
        }
        String fullName = readNonEmpty("Full name: ");
        String password = readNonEmpty("Password: ");
        System.out.println("Role:  1. Pharmacy / Inventory Staff   2. Nurse / Health Worker");
        String role = readIntInRange("Choose role: ", 1, 2) == 1 ? "PHARMACY_STAFF" : "NURSE";

        int id = USER_DAO.registerUser(username, password, fullName, role);
        if (id > 0) {
            System.out.println("  Registered (ID " + id + "). Please wait for an administrator to approve your account.");
        }
    }
}
