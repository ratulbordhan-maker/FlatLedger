package com.flatmeals.expense;

import com.flatmeals.common.LedgerRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A shared expense, e.g. groceries (table "expenses"). */
@Entity
@Table(name = "expenses")
public class Expense extends LedgerRecord {

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "description")
    private String description;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** true = counts toward the meal rate (groceries); false = recorded only (e.g. a repair). */
    @Column(name = "in_meal_rate", nullable = false)
    private boolean inMealRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "funded_by", nullable = false)
    private FundingSource fundedBy;

    /** Who paid: the cash holder (FLAT_CASH) or the member who paid personally (MEMBER_POCKET). */
    @Column(name = "holder_member_id", nullable = false)
    private Long holderMemberId;

    protected Expense() {
        // required by JPA
    }

    public Expense(Long monthId, Long createdBy, LocalDate expenseDate, String category, String description,
                   BigDecimal amount, boolean inMealRate, FundingSource fundedBy, Long holderMemberId) {
        super(monthId, createdBy);
        this.expenseDate = expenseDate;
        this.category = category;
        this.description = description;
        this.amount = amount;
        this.inMealRate = inMealRate;
        this.fundedBy = fundedBy;
        this.holderMemberId = holderMemberId;
    }

    public void update(LocalDate expenseDate, String category, String description, BigDecimal amount,
                             boolean inMealRate, FundingSource fundedBy, Long holderMemberId) {
        this.expenseDate = expenseDate;
        this.category = category;
        this.description = description;
        this.amount = amount;
        this.inMealRate = inMealRate;
        this.fundedBy = fundedBy;
        this.holderMemberId = holderMemberId;
    }

    public boolean isPaidFromPocket() {
        return fundedBy == FundingSource.MEMBER_POCKET;
    }

    public LocalDate getExpenseDate() { return expenseDate; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public BigDecimal getAmount() { return amount; }
    public boolean isInMealRate() { return inMealRate; }
    public FundingSource getFundedBy() { return fundedBy; }
    public Long getHolderMemberId() { return holderMemberId; }
}
