package com.flatmeals.cash;

import com.flatmeals.common.LedgerRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Flat cash handed from one holder to another (table cash_transfers).
 * Not income, not an expense: the flat's total cash stays the same.
 */
@Entity
@Table(name = "cash_transfers")
public class CashTransfer extends LedgerRecord {

    @Column(name = "from_member_id", nullable = false)
    private Long fromMemberId;

    @Column(name = "to_member_id", nullable = false)
    private Long toMemberId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "transferred_on", nullable = false)
    private LocalDate transferredOn;

    @Column(name = "note")
    private String note;

    protected CashTransfer() {
        // required by JPA
    }

    public CashTransfer(Long monthId, Long createdBy, Long fromMemberId, Long toMemberId,
                        BigDecimal amount, LocalDate transferredOn, String note) {
        super(monthId, createdBy);
        this.fromMemberId = fromMemberId;
        this.toMemberId = toMemberId;
        this.amount = amount;
        this.transferredOn = transferredOn;
        this.note = note;
    }

    public Long getFromMemberId() { return fromMemberId; }
    public Long getToMemberId() { return toMemberId; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getTransferredOn() { return transferredOn; }
    public String getNote() { return note; }
}
