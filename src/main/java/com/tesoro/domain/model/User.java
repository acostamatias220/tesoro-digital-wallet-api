package com.tesoro.domain.model;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record User(
        UUID id, String email, String passwordHash, String fullName, Role role, boolean enabled, Instant createdAt) {

    public User {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(role, "role");
        email = normalizeEmail(email);
    }

    public static User register(String email, String passwordHash, String fullName, Role role, Instant now) {
        return new User(UUID.randomUUID(), email, passwordHash, fullName.strip(), role, true, now);
    }

    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
