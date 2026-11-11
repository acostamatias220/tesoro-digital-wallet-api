package com.tesoro.application.model;

import com.tesoro.domain.ledger.EntryType;
import com.tesoro.domain.model.Direction;
import java.time.Instant;

/** Optional filters for an account statement; {@code null} means "any". {@code to} is exclusive. */
public record MovementFilter(EntryType type, Direction direction, Instant from, Instant to) {

    public static MovementFilter none() {
        return new MovementFilter(null, null, null, null);
    }
}
