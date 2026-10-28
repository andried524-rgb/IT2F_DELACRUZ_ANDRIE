package com.healthcenter.model;

import java.util.List;

/** Account statuses stored in users.status. */
public final class Statuses {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String INACTIVE = "INACTIVE";

    /** Statuses an admin may assign when updating a user. */
    public static final List<String> ASSIGNABLE = List.of(APPROVED, PENDING);

    private Statuses() {
    }
}
