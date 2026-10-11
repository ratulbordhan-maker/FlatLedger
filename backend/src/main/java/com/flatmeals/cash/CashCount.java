package com.flatmeals.cash;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A physical count of the cash someone is holding (table cash_counts).
 * Counts don't move money; they are compared with the recorded figure.
 * The latest count per holder in a month is the one the report uses.
 */
@Entity
@Table(name = "cash_counts")
public class CashCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "month_id", nullable = false)
    private Long monthId;

    @Column(name = "holder_member_id", nullable = false)
    private Long holderMemberId;

    @Column(name = "counted_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal countedAmount;

    // Set by MySQL to the moment the count is saved
    @Column(name = "counted_at", insertable = false, updatable = false)
    private Instant countedAt;

    @Column(name = "note")
    private String note;

    @Column(name = "created_by")
    private Long createdBy;

    protected CashCount() {
        // required by JPA
    }

    public CashCount(Long monthId, Long holderMemberId, BigDecimal countedAmount, String note, Long createdBy) {
        this.monthId = monthId;
        this.holderMemberId = holderMemberId;
        this.countedAmount = countedAmount;
        this.note = note;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public Long getMonthId() { return monthId; }
    public Long getHolderMemberId() { return holderMemberId; }
    public BigDecimal getCountedAmount() { return countedAmount; }
    public Instant getCountedAt() { return countedAt; }
    public String getNote() { return note; }
    public Long getCreatedBy() { return createdBy; }
}
