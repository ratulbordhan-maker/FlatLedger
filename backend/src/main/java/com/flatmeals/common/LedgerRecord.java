package com.flatmeals.common;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import java.time.Instant;

/**
 * The columns every voidable financial record shares:
 * id, month_id, status, void_reason, created_by, created_at.
 *
 * @MappedSuperclass means "this is not a table on its own; its fields are
 * copied into the table of each entity that extends it" (Expense, Payment, ...).
 */
@MappedSuperclass
public abstract class LedgerRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "month_id", nullable = false)
    private Long monthId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RecordStatus status = RecordStatus.ACTIVE;

    @Column(name = "void_reason")
    private String voidReason;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected LedgerRecord() {
        // required by JPA
    }

    protected LedgerRecord(Long monthId, Long createdBy) {
        this.monthId = monthId;
        this.createdBy = createdBy;
    }

    public boolean isActive() {
        return status == RecordStatus.ACTIVE;
    }

    /** Cancels the record. It stays in the database and in the history, with the reason. */
    public void voidRecord(String reason) {
        if (!isActive()) {
            throw new ConflictException("This record is already voided");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to void a record");
        }
        this.status = RecordStatus.VOIDED;
        this.voidReason = reason.trim();
    }

    public Long getId() { return id; }
    public Long getMonthId() { return monthId; }
    public RecordStatus getStatus() { return status; }
    public String getVoidReason() { return voidReason; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
