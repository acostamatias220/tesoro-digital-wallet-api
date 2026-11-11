package com.tesoro.application.model;

import com.tesoro.domain.model.Role;
import java.time.Instant;
import java.util.UUID;

public record UserSummary(UUID id, String email, String fullName, Role role, boolean enabled, Instant createdAt) {}
