package com.flatmeals.payment;

import com.flatmeals.common.LedgerRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * An authorised change to a member's balance (table "adjustments").
 * Signed: +100 means the member owes 100 more, -100 means 100 credit.
 * Adjustments never touch cash.
 */
@Entity
@Table(name = "adjustments")
public class Adjustment extends LedgerRecord {

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private AdjustmentType type;

    @Column(name = "reason", nullable = false)
    private String reason;

    protected Adjustment() {
        // required by JPA
    }

    public Adjustment(Long monthId, Long createdBy, Long memberId, BigDecimal amount,
                      AdjustmentType type, String reason) {
        super(monthId, createdBy);
        this.memberId = memberId;
        this.amount = amount;
        this.type = type;
        this.reason = reason;
    }

    public Long getMemberId() { return memberId; }
    public BigDecimal getAmount() { return amount; }
    public AdjustmentType getType() { return type; }
    public String getReason() { return reason; }
}
