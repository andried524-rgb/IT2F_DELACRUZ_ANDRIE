package com.healthcenter.ui;

import com.healthcenter.dao.UserDao;
import com.healthcenter.model.Roles;
import com.healthcenter.model.Statuses;
import com.healthcenter.model.User;
import com.healthcenter.util.PasswordUtil;

import java.util.Scanner;

/** Handles login, registration, and routing each user to the menu for their role. */
public class Authentication {

    private final UserDao userDao;
    private final Scanner scanner;

    public Authentication(UserDao userDao, Scanner scanner) {
        this.userDao = userDao;
        this.scanner = scanner;
    }

    public void start() {
        boolean running = true;
        while (running) {
            System.out.println();
            System.out.println("=====================================================");
            System.out.println("     HEALTH CENTER MEDICINE INVENTORY SYSTEM");
            System.out.println("=====================================================");
            System.out.println("1. Login");
            System.out.println("2. Register");
            System.out.println("0. Exit");
            System.out.print("Choose an option: ");

            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1" -> login();
                case "2" -> register();
                case "0" -> {
                    System.out.println("Goodbye!");
                    running = false;
                }
                default -> System.out.println("Invalid option. Please enter 1, 2, or 0.");
            }
        }
    }

    // ------------------------------------------------------------------ login

    private void login() {
        System.out.println("\n--- LOGIN ---");
        System.out.print("Username: ");
        String username = scanner.nextLine().trim();
        System.out.print("Password: ");
        String password = scanner.nextLine();

        User user = userDao.authenticate(username, PasswordUtil.hash(password));
        if (user == null) {
            System.out.println("Login failed: invalid username or password.");
            return;
        }
        if (Statuses.PENDING.equals(user.status())) {
            System.out.println("Your account is still PENDING. Please wait for an administrator to approve it.");
            return;
        }
        if (!Statuses.APPROVED.equals(user.status())) {
            System.out.println("Your account is " + user.status() + ". Please contact the administrator.");
            return;
        }

        System.out.println("\nLogin successful. Welcome, " + user.fullName() + " (" + user.role() + ")!");
        routeToMenu(user);
    }

    private void routeToMenu(User user) {
        // The same userDao and scanner are shared with every menu
        switch (user.role()) {
            case Roles.ADMIN -> new AdminMenu(userDao, scanner, user).show();
            case Roles.PHARMACY -> new PharmacyMenu(userDao, scanner, user).show();
            case Roles.NURSE -> new NurseMenu(userDao, scanner, user).show();
            default -> System.out.println("Unknown role '" + user.role() + "'. Please contact the administrator.");
        }
    }

    // --------------------------------------------------------------- register

    private void register() {
        System.out.println("\n--- REGISTER NEW ACCOUNT ---");

        String username = readUsername();
        String fullName = readNonBlank("Full name: ");
        String password = readPassword();
        String role = readRoleChoice();

        boolean saved = userDao.registerUser(username, PasswordUtil.hash(password), fullName, role);
        if (saved) {
            System.out.println("\nRegistration successful! Account '" + username + "' (" + role + ") was created.");
            System.out.println("Status: PENDING - an administrator must approve it before you can log in.");
        } else {
            System.out.println("Registration failed. Please try again.");
        }
    }

    private String readUsername() {
        while (true) {
            System.out.print("Username (max 15 characters, no spaces): ");
            String username = scanner.nextLine().trim();
            if (username.isEmpty() || username.contains(" ") || username.length() > 15) {
                System.out.println("Invalid username. Use 1-15 characters with no spaces.");
            } else if (userDao.usernameExists(username)) {
                System.out.println("That username is already taken. Choose another one.");
            } else {
                return username;
            }
        }
    }

    private String readPassword() {
        while (true) {
            System.out.print("Password (at least 6 characters): ");
            String password = scanner.nextLine();
            if (password.length() >= 6) {
                return password;
            }
            System.out.println("Password is too short.");
        }
    }

    private String readNonBlank(String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = scanner.nextLine().trim();
            if (!value.isEmpty()) {
                return value;
            }
            System.out.println("This field cannot be empty.");
        }
    }

    private String readRoleChoice() {
        while (true) {
            System.out.println("Select your role:");
            System.out.println("  1. ADMIN    - Health Center Administrator");
            System.out.println("  2. PHARMACY - Pharmacy / Inventory Staff");
            System.out.println("  3. NURSE    - Nurse / Health Worker");
            System.out.print("Role (1-3): ");
            switch (scanner.nextLine().trim()) {
                case "1" -> {
                    return Roles.ADMIN;
                }
                case "2" -> {
                    return Roles.PHARMACY;
                }
                case "3" -> {
                    return Roles.NURSE;
                }
                default -> System.out.println("Invalid role choice. Enter 1, 2, or 3.");
            }
        }
    }
}
