package com.flatmeals.account;

import com.flatmeals.month.MonthMemberId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * A member's account for a CLOSED month, frozen at the moment of closing
 * (table month_member_summary). The next month's opening balance is read
 * from closingBalance, so old months never need recalculating.
 */
@Entity
@Table(name = "month_member_summary")
@IdClass(MonthMemberId.class)
public class MonthMemberSummary {

    @Id
    @Column(name = "month_id")
    private Long monthId;

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "opening_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal openingBalance;

    @Column(name = "meal_count", nullable = false, precision = 8, scale = 2)
    private BigDecimal mealCount;

    @Column(name = "meal_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal mealCost;

    /** Payments minus refunds. */
    @Column(name = "payments_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal paymentsTotal;

    @Column(name = "adjustments_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal adjustmentsTotal;

    @Column(name = "pocket_credit", nullable = false, precision = 12, scale = 2)
    private BigDecimal pocketCredit;

    @Column(name = "closing_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal closingBalance;

    protected MonthMemberSummary() {
        // required by JPA
    }

    public MonthMemberSummary(Long monthId, Long memberId, BigDecimal openingBalance, BigDecimal mealCount,
                              BigDecimal mealCost, BigDecimal paymentsTotal, BigDecimal adjustmentsTotal,
                              BigDecimal pocketCredit, BigDecimal closingBalance) {
        this.monthId = monthId;
        this.memberId = memberId;
        this.openingBalance = openingBalance;
        this.mealCount = mealCount;
        this.mealCost = mealCost;
        this.paymentsTotal = paymentsTotal;
        this.adjustmentsTotal = adjustmentsTotal;
        this.pocketCredit = pocketCredit;
        this.closingBalance = closingBalance;
    }

    public Long getMonthId() { return monthId; }
    public Long getMemberId() { return memberId; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public BigDecimal getMealCount() { return mealCount; }
    public BigDecimal getMealCost() { return mealCost; }
    public BigDecimal getPaymentsTotal() { return paymentsTotal; }
    public BigDecimal getAdjustmentsTotal() { return adjustmentsTotal; }
    public BigDecimal getPocketCredit() { return pocketCredit; }
    public BigDecimal getClosingBalance() { return closingBalance; }
}
