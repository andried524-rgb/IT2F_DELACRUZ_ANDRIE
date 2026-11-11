package com.yourdomain.model;

/** One row of the activity_logs table (immutable Java Record). */
public record ActivityLog(int id, int userId, String actionType, String description, String timestamp) {
}
