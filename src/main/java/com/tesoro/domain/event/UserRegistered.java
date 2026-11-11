package com.tesoro.domain.event;

import java.time.Instant;
import java.util.UUID;

public record UserRegistered(UUID userId, String email, Instant occurredAt) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "User";
    }

    @Override
    public UUID aggregateId() {
        return userId;
    }
}
