package com.flatmeals.cash;

import com.flatmeals.common.LedgerRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * An explained correction to a holder's cash (table cash_adjustments).
 * Signed: +500 adds cash to the holder, -50 removes it.
 * It never changes anyone's personal balance.
 */
@Entity
@Table(name = "cash_adjustments")
public class CashAdjustment extends LedgerRecord {

    @Column(name = "holder_member_id", nullable = false)
    private Long holderMemberId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CashAdjustmentType type;

    @Column(name = "reason", nullable = false)
    private String reason;

    protected CashAdjustment() {
        // required by JPA
    }

    public CashAdjustment(Long monthId, Long createdBy, Long holderMemberId, BigDecimal amount,
                          CashAdjustmentType type, String reason) {
        super(monthId, createdBy);
        this.holderMemberId = holderMemberId;
        this.amount = amount;
        this.type = type;
        this.reason = reason;
    }

    public Long getHolderMemberId() { return holderMemberId; }
    public BigDecimal getAmount() { return amount; }
    public CashAdjustmentType getType() { return type; }
    public String getReason() { return reason; }
}
