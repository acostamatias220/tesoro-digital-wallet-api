package com.tesoro.application.model;

import java.util.UUID;

/** Authenticated caller of a use case, resolved by the inbound adapter. */
public record Requester(UUID userId, boolean admin) {

    public static Requester user(UUID userId) {
        return new Requester(userId, false);
    }
}
