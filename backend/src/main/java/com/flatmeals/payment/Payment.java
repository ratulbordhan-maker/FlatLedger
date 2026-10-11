package com.flatmeals.payment;

import com.flatmeals.common.LedgerRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Money between a member and the flat (table "payments").
 *
 * One row is ONE real-world event with two effects:
 *   - the member's personal balance changes, and
 *   - the cash held by cashHolderId changes.
 * Because both come from the same row, a payment can never be counted twice.
 */
@Entity
@Table(name = "payments")
public class Payment extends LedgerRecord {

    /** The member whose account this affects. */
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** PAYMENT: who received the cash. REFUND: who handed the cash back. */
    @Column(name = "received_by_member_id", nullable = false)
    private Long cashHolderId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private PaymentType type;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    @Column(name = "note")
    private String note;

    protected Payment() {
        // required by JPA
    }

    public Payment(Long monthId, Long createdBy, Long memberId, Long cashHolderId, BigDecimal amount,
                   PaymentType type, LocalDate paidOn, String note) {
        super(monthId, createdBy);
        this.memberId = memberId;
        this.cashHolderId = cashHolderId;
        this.amount = amount;
        this.type = type;
        this.paidOn = paidOn;
        this.note = note;
    }

    public Long getMemberId() { return memberId; }
    public Long getCashHolderId() { return cashHolderId; }
    public BigDecimal getAmount() { return amount; }
    public PaymentType getType() { return type; }
    public LocalDate getPaidOn() { return paidOn; }
    public String getNote() { return note; }
}
