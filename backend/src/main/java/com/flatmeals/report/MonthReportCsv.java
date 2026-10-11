package com.flatmeals.report;

import com.flatmeals.account.LedgerDtos.AccountsResponse;
import com.flatmeals.account.LedgerDtos.CashHolderPosition;
import com.flatmeals.account.LedgerDtos.CashReportResponse;
import com.flatmeals.account.LedgerDtos.MemberAccount;
import com.flatmeals.account.LedgerDtos.ReconciliationRow;
import com.flatmeals.month.MonthStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Turns a MonthReport into a CSV file that opens cleanly in Excel or Google Sheets.
 *
 * Pure Java (no Spring, no database), so it is easy to test.
 *
 * Rules:
 *  - The file starts with a "BOM" (an invisible marker) so Excel reads it as
 *    UTF-8 and shows Bangla names and the ৳ sign correctly.
 *  - Money is a plain number with 2 decimals (1250.50): no ৳ sign and no commas,
 *    so spreadsheets can add it up.
 *  - Text containing a comma, quote or line break is wrapped in quotes.
 *  - Text starting with = + - @ gets a ' in front. Otherwise a spreadsheet
 *    would run it as a formula ("CSV injection"). Numbers are never changed,
 *    so negative amounts stay numbers.
 *  - Lines end with \r\n, the CSV standard.
 */
public final class MonthReportCsv {

    static final String BOM = "﻿";
    private static final String NEWLINE = "\r\n";

    private final StringBuilder out = new StringBuilder();

    private MonthReportCsv() {
    }

    public static String fileName(int year, int month) {
        return String.format("flat-meals-%04d-%02d.csv", year, month);
    }

    public static byte[] bytes(MonthReport report) {
        return build(report).getBytes(StandardCharsets.UTF_8);
    }

    public static String build(MonthReport report) {
        MonthReportCsv csv = new MonthReportCsv();
        csv.out.append(BOM);
        csv.summary(report.accounts());
        csv.members(report.accounts().accounts());
        csv.expenses(report.expenses());
        csv.payments(report.payments());
        csv.adjustments(report.adjustments());
        csv.cash(report.cash());
        return csv.out.toString();
    }

    // ------------------------------------------------------------ sections

    private void summary(AccountsResponse a) {
        row(text("Flat meal report"), text(a.label()));
        row(text("Status"), text(a.status() == MonthStatus.CLOSED ? "Closed (final)" : "Open (figures can still change)"));
        row(text("Total meals"), number(a.totalMeals()));
        row(text("Meal expense"), money(a.totalExpense()));
        row(text("Meal rate per meal"), rate(a.mealRate()));
        row(text("Members owe the flat"), money(a.totalOwed()));
        row(text("Flat owes members (credit)"), money(a.totalCredit()));
        if (a.warning() != null && !a.warning().isBlank()) {
            row(text("Note"), text(a.warning()));
        }
        row(text("Note"), text("Meal costs are rounded to the poisha so they add up exactly to the meal expense."));
    }

    private void members(List<MemberAccount> accounts) {
        section("Members",
                "Member", "Brought forward", "Meals", "Meal cost", "Payments", "Refunds",
                "Adjustments", "Bought for the flat", "Balance", "Owes or credit");
        for (MemberAccount m : accounts) {
            row(text(m.name()), money(m.openingBalance()), number(m.mealCount()), money(m.mealCost()),
                    money(m.payments()), money(m.refunds()), money(m.adjustments()), money(m.pocketCredit()),
                    money(m.closingBalance()), text(balanceWord(m.closingBalance())));
        }
        row(text("Total"),
                money(sum(accounts, MemberAccount::openingBalance)),
                number(sum(accounts, MemberAccount::mealCount)),
                money(sum(accounts, MemberAccount::mealCost)),
                money(sum(accounts, MemberAccount::payments)),
                money(sum(accounts, MemberAccount::refunds)),
                money(sum(accounts, MemberAccount::adjustments)),
                money(sum(accounts, MemberAccount::pocketCredit)),
                money(sum(accounts, MemberAccount::closingBalance)),
                "");
    }

    private void expenses(List<MonthReport.ExpenseLine> expenses) {
        section("Expenses",
                "Date", "Category", "Description", "Amount", "Paid from", "In meal rate", "Status", "Cancel reason");
        BigDecimal all = BigDecimal.ZERO;
        BigDecimal meal = BigDecimal.ZERO;
        for (MonthReport.ExpenseLine e : expenses) {
            row(date(e.date()), text(e.category()), text(e.description()), money(e.amount()), text(e.paidFrom()),
                    text(e.inMealRate() ? "Yes" : "No"), text(statusWord(e.cancelled())), text(e.cancelReason()));
            if (!e.cancelled()) {
                all = all.add(e.amount());
                if (e.inMealRate()) {
                    meal = meal.add(e.amount());
                }
            }
        }
        row(text("Total (not cancelled)"), "", "", money(all));
        row(text("Counted in the meal rate"), "", "", money(meal));
    }

    private void payments(List<MonthReport.PaymentLine> payments) {
        section("Payments and refunds",
                "Date", "Member", "Type", "Amount", "Cash handled by", "Note", "Status", "Cancel reason");
        for (MonthReport.PaymentLine p : payments) {
            row(date(p.date()), text(p.member()), text(p.type()), money(p.amount()), text(p.cashHandledBy()),
                    text(p.note()), text(statusWord(p.cancelled())), text(p.cancelReason()));
        }
    }

    private void adjustments(List<MonthReport.AdjustmentLine> adjustments) {
        section("Adjustments (positive = owes more, negative = credit)",
                "Member", "Type", "Amount", "Reason", "Status", "Cancel reason");
        for (MonthReport.AdjustmentLine a : adjustments) {
            row(text(a.member()), text(a.type()), money(a.amount()), text(a.reason()),
                    text(statusWord(a.cancelled())), text(a.cancelReason()));
        }
    }

    private void cash(CashReportResponse cash) {
        section("Flat cash by holder",
                "Holder", "Cash at start", "Cash in", "Cash out", "Cash now");
        for (CashHolderPosition h : cash.holders()) {
            row(text(h.name()), money(h.openingCash()), money(h.cashIn()), money(h.cashOut()), money(h.closingCash()));
        }
        row(text("Total"), "", "", "", money(cash.totalCash()));

        section("Cash count check",
                "Holder", "Records say", "Counted", "Difference", "Result");
        for (ReconciliationRow r : cash.reconciliation()) {
            row(text(r.name()), money(r.recorded()), money(r.counted()), money(r.difference()),
                    text(switch (r.status()) {
                        case MATCHED -> "Matches";
                        case SHORT -> "Short";
                        case OVER -> "Over";
                        case NOT_COUNTED -> "Not counted";
                    }));
        }
    }

    // ------------------------------------------------------------ cells

    private void section(String title, String... headings) {
        out.append(NEWLINE);
        row(text(title));
        List<String> cells = new ArrayList<>();
        for (String heading : headings) {
            cells.add(text(heading));
        }
        row(cells.toArray(new String[0]));
    }

    private void row(String... cells) {
        out.append(String.join(",", cells)).append(NEWLINE);
    }

    /** A text cell: protected against formulas and quoted when needed. */
    static String text(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String safe = value;
        char first = safe.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            safe = "'" + safe;
        }
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            safe = "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    /** Money: 2 decimals, minus sign for negatives, never quoted or prefixed. */
    static String money(BigDecimal value) {
        return value == null ? "" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Meal counts: 45.5 stays 45.5, 60.00 becomes 60. */
    static String number(BigDecimal value) {
        if (value == null) {
            return "";
        }
        BigDecimal plain = value.stripTrailingZeros();
        return (plain.scale() < 0 ? plain.setScale(0) : plain).toPlainString();
    }

    /** The meal rate with 4 decimals, so the maths can be checked by hand. */
    static String rate(BigDecimal value) {
        return value == null ? "" : value.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private static String date(LocalDate value) {
        return value == null ? "" : value.toString();
    }

    private static String statusWord(boolean cancelled) {
        return cancelled ? "Cancelled" : "Active";
    }

    private static String balanceWord(BigDecimal balance) {
        int sign = balance == null ? 0 : balance.signum();
        return sign > 0 ? "Owes" : sign < 0 ? "Credit" : "Settled";
    }

    private static <T> BigDecimal sum(List<T> items, Function<T, BigDecimal> field) {
        BigDecimal total = BigDecimal.ZERO;
        for (T item : items) {
            BigDecimal value = field.apply(item);
            if (value != null) {
                total = total.add(value);
            }
        }
        return total;
    }
}
