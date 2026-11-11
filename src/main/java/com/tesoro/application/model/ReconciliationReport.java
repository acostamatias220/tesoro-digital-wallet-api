package com.tesoro.application.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReconciliationReport(
        UUID id,
        String trigger,
        Status status,
        long walletsChecked,
        long entriesChecked,
        List<ReconciliationIssue> issues,
        Instant startedAt,
        Instant finishedAt) {

    public enum Status {
        OK,
        MISMATCH
    }
}
