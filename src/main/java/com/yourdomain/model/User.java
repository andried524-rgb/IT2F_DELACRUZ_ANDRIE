package com.yourdomain.model;

/** A system user (Administrator, Pharmacy Staff or Nurse). */
public record User(int id, String username, String fullName, String role, String status) {
}
