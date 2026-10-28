package com.healthcenter.model;

import java.util.List;

/** The three target user roles from the approved system proposal. */
public final class Roles {

    public static final String ADMIN = "ADMIN";        // Health Center Administrator
    public static final String PHARMACY = "PHARMACY";  // Pharmacy / Inventory Staff
    public static final String NURSE = "NURSE";        // Nurse / Health Worker

    public static final List<String> ALL = List.of(ADMIN, PHARMACY, NURSE);

    private Roles() {
    }
}
