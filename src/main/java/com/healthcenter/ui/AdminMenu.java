package com.healthcenter.ui;

import com.healthcenter.dao.UserDao;
import com.healthcenter.model.Roles;
import com.healthcenter.model.Statuses;
import com.healthcenter.model.User;

import java.util.List;
import java.util.Scanner;

/** Menu for the Health Center Administrator (user management). */
public class AdminMenu {

    private final UserDao userDao;
    private final Scanner scanner;
    private final User currentAdmin;

    public AdminMenu(UserDao userDao, Scanner scanner, User currentAdmin) {
        this.userDao = userDao;
        this.scanner = scanner;
        this.currentAdmin = currentAdmin;
    }

    public void show() {
        boolean running = true;
        while (running) {
            System.out.println();
            System.out.println("=============== ADMIN MENU ===============");
            System.out.println("Logged in as: " + currentAdmin.fullName() + " (" + currentAdmin.role() + ")");
            System.out.println("1. View All System Users");
            System.out.println("2. View Pending Registrations");
            System.out.println("3. Approve User Account");
            System.out.println("0. Logout");
            System.out.print("Choose an option: ");

            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1" -> viewAllUsers();
                case "2" -> viewPendingUsers();
                case "3" -> approveUser();
                case "0" -> {
                    System.out.println("Logged out.");
                    running = false;
                }
                default -> System.out.println("Invalid option. Please choose from the menu.");
            }
        }
    }

    // ------------------------------------------------------------ actions

    private void viewAllUsers() {
        System.out.println("\n--- ALL SYSTEM USERS ---");
        printUserTable(userDao.getAllUsers());
    }

    private void viewPendingUsers() {
        System.out.println("\n--- PENDING REGISTRATIONS ---");
        printUserTable(userDao.getPendingUsers());
    }

    private void approveUser() {
        List<User> pending = userDao.getPendingUsers();
        System.out.println("\n--- APPROVE USER ACCOUNT ---");
        if (pending.isEmpty()) {
            System.out.println("There are no pending registrations to approve.");
            return;
        }
        printUserTable(pending);

        while (true) {
            int targetId = readIntInput("Enter the User ID to approve (0 to cancel): ");
            if (targetId == 0) {
                System.out.println("Approval cancelled.");
                return;
            }
            if (!containsId(pending, targetId)) {
                System.out.println("User ID " + targetId + " is not in the pending list. Try again.");
                continue;
            }
            if (userDao.approveUser(targetId)) {
                System.out.println("SUCCESS: User ID " + targetId + " has been APPROVED.");
            } else {
                System.out.println("Approval failed. Please try again.");
            }
            return;
        }
    }

    // ------------------------------------------------------ input helpers

    /** Keeps asking until the user types a whole number (never throws). */
    private int readIntInput(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter numbers only.");
            }
        }
    }

    // -------------------------------------------------------- table output

    private boolean containsId(List<User> users, int id) {
        return users.stream().anyMatch(u -> u.id() == id);
    }

    /** Prints any list of users as an aligned table. */
    private void printUserTable(List<User> users) {
        if (users.isEmpty()) {
            System.out.println("No users to display.");
            return;
        }
        String border = String.format("+-%s-+-%s-+-%s-+-%s-+",
                "-".repeat(5), "-".repeat(15), "-".repeat(12), "-".repeat(10));
        System.out.println(border);
        System.out.println(String.format("| %-5s | %-15s | %-12s | %-10s |", "ID", "USERNAME", "ROLE", "STATUS"));
        System.out.println(border);
        for (User u : users) {
            System.out.println(String.format("| %-5s | %-15s | %-12s | %-10s |",
                    u.id(), u.username(), u.role(), u.status()));
        }
        System.out.println(border);
        System.out.println("Total: " + users.size() + " user(s)");
    }
}
