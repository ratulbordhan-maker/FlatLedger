package com.flatmeals.cash;

import com.flatmeals.common.LedgerRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cash the flat received from somewhere other than member payments,
 * e.g. selling an old gas cylinder (table cash_receipts).
 */
@Entity
@Table(name = "cash_receipts")
public class CashReceipt extends LedgerRecord {

    @Column(name = "holder_member_id", nullable = false)
    private Long holderMemberId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "received_on", nullable = false)
    private LocalDate receivedOn;

    protected CashReceipt() {
        // required by JPA
    }

    public CashReceipt(Long monthId, Long createdBy, Long holderMemberId, BigDecimal amount,
                       String source, LocalDate receivedOn) {
        super(monthId, createdBy);
        this.holderMemberId = holderMemberId;
        this.amount = amount;
        this.source = source;
        this.receivedOn = receivedOn;
    }

    public Long getHolderMemberId() { return holderMemberId; }
    public BigDecimal getAmount() { return amount; }
    public String getSource() { return source; }
    public LocalDate getReceivedOn() { return receivedOn; }
}
