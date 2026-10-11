package com.flatmeals.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * A holder's flat cash for a CLOSED month, frozen at closing
 * (table month_cash_summary). The next month's opening cash comes from closingCash.
 */
@Entity
@Table(name = "month_cash_summary")
@IdClass(MonthHolderId.class)
public class MonthCashSummary {

    @Id
    @Column(name = "month_id")
    private Long monthId;

    @Id
    @Column(name = "holder_member_id")
    private Long holderMemberId;

    @Column(name = "opening_cash", nullable = false, precision = 12, scale = 2)
    private BigDecimal openingCash;

    @Column(name = "cash_in", nullable = false, precision = 12, scale = 2)
    private BigDecimal cashIn;

    @Column(name = "cash_out", nullable = false, precision = 12, scale = 2)
    private BigDecimal cashOut;

    @Column(name = "closing_cash", nullable = false, precision = 12, scale = 2)
    private BigDecimal closingCash;

    protected MonthCashSummary() {
        // required by JPA
    }

    public MonthCashSummary(Long monthId, Long holderMemberId, BigDecimal openingCash,
                            BigDecimal cashIn, BigDecimal cashOut, BigDecimal closingCash) {
        this.monthId = monthId;
        this.holderMemberId = holderMemberId;
        this.openingCash = openingCash;
        this.cashIn = cashIn;
        this.cashOut = cashOut;
        this.closingCash = closingCash;
    }

    public Long getMonthId() { return monthId; }
    public Long getHolderMemberId() { return holderMemberId; }
    public BigDecimal getOpeningCash() { return openingCash; }
    public BigDecimal getCashIn() { return cashIn; }
    public BigDecimal getCashOut() { return cashOut; }
    public BigDecimal getClosingCash() { return closingCash; }
}
