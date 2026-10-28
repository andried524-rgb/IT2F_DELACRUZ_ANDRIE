package com.healthcenter;

import com.healthcenter.dao.DatabaseInitializer;
import com.healthcenter.dao.UserDao;
import com.healthcenter.ui.Authentication;

import java.util.Scanner;

/**
 * Single entry point of the application.
 * Flow: Initialize Database -> Instantiate DAOs and utilities -> Authentication.start()
 */
public class Main {

    public static void main(String[] args) {
        // 1. Make sure the database and all tables exist
        DatabaseInitializer.initialize();

        // 2. Create ONE shared UserDao and ONE shared Scanner
        UserDao userDao = new UserDao();
        Scanner scanner = new Scanner(System.in);

        // 3. Hand control over to the authentication handler
        Authentication authentication = new Authentication(userDao, scanner);
        authentication.start();

        scanner.close();
    }
}
