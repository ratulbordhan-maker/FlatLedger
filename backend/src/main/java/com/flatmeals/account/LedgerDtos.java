package com.flatmeals.account;

import com.flatmeals.calc.ReconciliationLine;
import com.flatmeals.month.MonthStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Personal accounts and the cash report. */
public final class LedgerDtos {

    private LedgerDtos() {
    }

    /**
     * One line of a member's transaction history.
     * amount is the effect on the balance: positive = owes more, negative = owes less.
     * Voided lines are shown (voided = true) but do not count.
     */
    public record AccountTransaction(
            LocalDate date,
            String type,
            String description,
            BigDecimal amount,
            boolean voided,
            String voidReason) {
    }

    /**
     * A member's account for one month.
     * closing = opening + mealCost + adjustments + refunds - payments - pocketCredit
     * balanceStatus: OWES (positive), CREDIT (negative) or SETTLED (zero).
     */
    public record MemberAccount(
            Long memberId,
            String name,
            BigDecimal openingBalance,
            BigDecimal mealCount,
            BigDecimal mealCost,
            BigDecimal payments,
            BigDecimal refunds,
            BigDecimal adjustments,
            BigDecimal pocketCredit,
            BigDecimal closingBalance,
            String balanceStatus,
            List<AccountTransaction> transactions) {
    }

    public record AccountsResponse(
            int year,
            int month,
            String label,
            MonthStatus status,
            BigDecimal totalMeals,
            BigDecimal totalExpense,
            BigDecimal mealRate,
            String warning,
            List<MemberAccount> accounts,
            BigDecimal totalOwed,       // sum of positive balances
            BigDecimal totalCredit) {   // sum of credits, shown as a positive number
    }

    /** Flat cash held by one person, which is separate from their personal balance. */
    public record CashHolderPosition(
            Long memberId,
            String name,
            BigDecimal openingCash,
            BigDecimal cashIn,
            BigDecimal cashOut,
            BigDecimal closingCash) {
    }

    public record ReconciliationRow(
            Long memberId,
            String name,
            BigDecimal recorded,
            BigDecimal counted,
            BigDecimal difference,
            ReconciliationLine.Status status) {
    }

    public record CashReportResponse(
            int year,
            int month,
            String label,
            MonthStatus status,
            List<CashHolderPosition> holders,
            BigDecimal totalCash,
            List<ReconciliationRow> reconciliation) {
    }
}
