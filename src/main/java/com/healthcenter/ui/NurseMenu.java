package com.healthcenter.ui;

import com.healthcenter.dao.UserDao;
import com.healthcenter.model.User;

import java.util.Scanner;

/** Menu for Nurse / Health Worker (features are added in later activities). */
public class NurseMenu {

    private final UserDao userDao;
    private final Scanner scanner;
    private final User currentUser;

    public NurseMenu(UserDao userDao, Scanner scanner, User currentUser) {
        this.userDao = userDao;
        this.scanner = scanner;
        this.currentUser = currentUser;
    }

    public void show() {
        boolean running = true;
        while (running) {
            System.out.println();
            System.out.println("=============== NURSE / HEALTH WORKER MENU ===============");
            System.out.println("Logged in as: " + currentUser.fullName() + " (" + currentUser.role() + ")");
            System.out.println("1. (Coming soon) Menu features");
            System.out.println("0. Logout");
            System.out.print("Choose an option: ");

            String choice = scanner.nextLine().trim();
            if (choice.equals("0")) {
                System.out.println("Logged out.");
                running = false;
            } else if (choice.equals("1")) {
                System.out.println("This feature will be added in a later activity.");
            } else {
                System.out.println("Invalid option.");
            }
        }
    }
}
