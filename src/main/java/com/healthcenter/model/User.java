package com.healthcenter.model;

/** Immutable snapshot of a row in the users table (password hash is never exposed). */
public record User(int id, String username, String fullName, String role, String status) {
}
