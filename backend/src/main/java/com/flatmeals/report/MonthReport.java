package com.flatmeals.report;

import com.flatmeals.account.LedgerDtos.AccountsResponse;
import com.flatmeals.account.LedgerDtos.CashReportResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything in one month's report, already turned into names and words
 * (no database ids), so MonthReportCsv only has to lay it out.
 *
 * accounts and cash come straight from LedgerService, so the report always
 * shows exactly the same numbers as the app.
 */
public record MonthReport(
        AccountsResponse accounts,
        CashReportResponse cash,
        List<ExpenseLine> expenses,
        List<PaymentLine> payments,
        List<AdjustmentLine> adjustments) {

    /** paidFrom is e.g. "Flat cash held by Anila" or "Anila's own money". */
    public record ExpenseLine(
            LocalDate date,
            String category,
            String description,
            BigDecimal amount,
            String paidFrom,
            boolean inMealRate,
            boolean cancelled,
            String cancelReason) {
    }

    /** type is "Payment" or "Refund"; cashHandledBy is who received or paid out the cash. */
    public record PaymentLine(
            LocalDate date,
            String member,
            String type,
            BigDecimal amount,
            String cashHandledBy,
            String note,
            boolean cancelled,
            String cancelReason) {
    }

    /** amount is signed like a balance: positive = owes more, negative = credit. */
    public record AdjustmentLine(
            String member,
            String type,
            BigDecimal amount,
            String reason,
            boolean cancelled,
            String cancelReason) {
    }
}
