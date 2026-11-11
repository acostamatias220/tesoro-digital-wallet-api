package com.tesoro.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "reconciliation_runs")
public class ReconciliationRunEntity {

    @Id
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "trigger_source")
    private String trigger;

    @Column(name = "status")
    private String status;

    @Column(name = "wallets_checked")
    private long walletsChecked;

    @Column(name = "entries_checked")
    private long entriesChecked;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "issues")
    private String issues;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    public ReconciliationRunEntity() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTrigger() {
        return trigger;
    }

    public void setTrigger(String trigger) {
        this.trigger = trigger;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getWalletsChecked() {
        return walletsChecked;
    }

    public void setWalletsChecked(long walletsChecked) {
        this.walletsChecked = walletsChecked;
    }

    public long getEntriesChecked() {
        return entriesChecked;
    }

    public void setEntriesChecked(long entriesChecked) {
        this.entriesChecked = entriesChecked;
    }

    public String getIssues() {
        return issues;
    }

    public void setIssues(String issues) {
        this.issues = issues;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }
}
