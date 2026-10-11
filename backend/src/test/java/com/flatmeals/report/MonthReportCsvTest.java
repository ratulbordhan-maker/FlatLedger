package com.flatmeals.report;

import com.flatmeals.account.LedgerDtos.AccountsResponse;
import com.flatmeals.account.LedgerDtos.CashHolderPosition;
import com.flatmeals.account.LedgerDtos.CashReportResponse;
import com.flatmeals.account.LedgerDtos.MemberAccount;
import com.flatmeals.account.LedgerDtos.ReconciliationRow;
import com.flatmeals.calc.ReconciliationLine;
import com.flatmeals.month.MonthStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MonthReportCsvTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    /**
     * October 2026: Anila ate 60 meals, Srijila 40; the meal expense was ৳10,000,
     * so the rate is ৳100 a meal. Anila paid ৳5,000; Srijila paid ৳5,000 and
     * also bought ৳500 of groceries with her own money.
     */
    static MonthReport october() {
        MemberAccount anila = new MemberAccount(1L, "Anila", bd("0.00"), bd("60.00"), bd("6000.00"),
                bd("5000.00"), bd("0.00"), bd("0.00"), bd("0.00"), bd("1000.00"), "OWES", List.of());
        MemberAccount srijila = new MemberAccount(2L, "Srijila", bd("0.00"), bd("40.00"), bd("4000.00"),
                bd("5000.00"), bd("0.00"), bd("0.00"), bd("500.00"), bd("-1500.00"), "CREDIT", List.of());
        AccountsResponse accounts = new AccountsResponse(2026, 10, "October 2026", MonthStatus.OPEN,
                bd("100.00"), bd("10000.00"), bd("100.000000"), null, List.of(anila, srijila),
                bd("1000.00"), bd("1500.00"));

        CashReportResponse cash = new CashReportResponse(2026, 10, "October 2026", MonthStatus.OPEN,
                List.of(new CashHolderPosition(1L, "Anila", bd("0.00"), bd("10000.00"), bd("9500.00"), bd("500.00"))),
                bd("500.00"),
                List.of(new ReconciliationRow(1L, "Anila", bd("500.00"), bd("450.00"), bd("-50.00"),
                        ReconciliationLine.Status.SHORT)));

        List<MonthReport.ExpenseLine> expenses = List.of(
                new MonthReport.ExpenseLine(LocalDate.of(2026, 10, 2), "Groceries", "Rice, dal, oil",
                        bd("9500.00"), "Flat cash held by Anila", true, false, null),
                new MonthReport.ExpenseLine(LocalDate.of(2026, 10, 9), "Groceries", "Fish",
                        bd("500.00"), "Srijila's own money", true, false, null),
                new MonthReport.ExpenseLine(LocalDate.of(2026, 10, 9), "Groceries", "Fish",
                        bd("500.00"), "Srijila's own money", true, true, "Entered twice"));
        List<MonthReport.PaymentLine> payments = List.of(
                new MonthReport.PaymentLine(LocalDate.of(2026, 10, 1), "Anila", "Payment", bd("5000.00"),
                        "Anila", null, false, null),
                new MonthReport.PaymentLine(LocalDate.of(2026, 10, 1), "Srijila", "Payment", bd("5000.00"),
                        "Anila", "bKash", false, null));
        return new MonthReport(accounts, cash, expenses, payments, List.of());
    }

    @Test
    void startsWithTheExcelMarkerAndUsesWindowsLineEnds() {
        String csv = MonthReportCsv.build(october());
        assertThat(csv).startsWith("﻿Flat meal report,October 2026\r\n");
        assertThat(MonthReportCsv.bytes(october())).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(new String(MonthReportCsv.bytes(october()), StandardCharsets.UTF_8)).isEqualTo(csv);
    }

    @Test
    void showsEachMemberAndTotalsThatAddUp() {
        String csv = MonthReportCsv.build(october());
        assertThat(csv).contains("Meal rate per meal,100.0000\r\n");
        assertThat(csv).contains("Anila,0.00,60,6000.00,5000.00,0.00,0.00,0.00,1000.00,Owes\r\n");
        assertThat(csv).contains("Srijila,0.00,40,4000.00,5000.00,0.00,0.00,500.00,-1500.00,Credit\r\n");
        // the meal costs add up to the meal expense, and balances to owed − credit
        assertThat(csv).contains("Total,0.00,100,10000.00,10000.00,0.00,0.00,500.00,-500.00,\r\n");
    }

    @Test
    void cancelledExpensesAreListedButNotCounted() {
        String csv = MonthReportCsv.build(october());
        assertThat(csv).contains("2026-10-09,Groceries,Fish,500.00,Srijila's own money,Yes,Cancelled,Entered twice\r\n");
        assertThat(csv).contains("Total (not cancelled),,,10000.00\r\n");
        assertThat(csv).contains("Counted in the meal rate,,,10000.00\r\n");
    }

    @Test
    void showsTheCashCheck() {
        String csv = MonthReportCsv.build(october());
        assertThat(csv).contains("Anila,0.00,10000.00,9500.00,500.00\r\n");
        assertThat(csv).contains("Anila,500.00,450.00,-50.00,Short\r\n");
    }

    @Test
    void quotesTextWithCommasQuotesAndLineBreaks() {
        assertThat(MonthReportCsv.text("Rice, dal")).isEqualTo("\"Rice, dal\"");
        assertThat(MonthReportCsv.text("5\" plate")).isEqualTo("\"5\"\" plate\"");
        assertThat(MonthReportCsv.text("two\nlines")).isEqualTo("\"two\nlines\"");
        assertThat(MonthReportCsv.text("Plain")).isEqualTo("Plain");
        assertThat(MonthReportCsv.text(null)).isEmpty();
    }

    @Test
    void stopsTextFromRunningAsASpreadsheetFormula() {
        assertThat(MonthReportCsv.text("=HYPERLINK(\"http://bad\")")).isEqualTo("\"'=HYPERLINK(\"\"http://bad\"\")\"");
        assertThat(MonthReportCsv.text("+880 1711")).isEqualTo("'+880 1711");
        assertThat(MonthReportCsv.text("-oops")).isEqualTo("'-oops");
        assertThat(MonthReportCsv.text("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        // numbers are not text: negative money stays a real number
        assertThat(MonthReportCsv.money(bd("-1500"))).isEqualTo("-1500.00");
    }

    @Test
    void formatsNumbersForSpreadsheets() {
        assertThat(MonthReportCsv.money(bd("1250.5"))).isEqualTo("1250.50");
        assertThat(MonthReportCsv.money(bd("100000"))).isEqualTo("100000.00");
        assertThat(MonthReportCsv.number(bd("45.50"))).isEqualTo("45.5");
        assertThat(MonthReportCsv.number(bd("60.00"))).isEqualTo("60");
        assertThat(MonthReportCsv.number(bd("0.00"))).isEqualTo("0");
        assertThat(MonthReportCsv.rate(bd("97.165992"))).isEqualTo("97.1660");
    }

    @Test
    void namesTheFileByMonth() {
        assertThat(MonthReportCsv.fileName(2026, 3)).isEqualTo("flat-meals-2026-03.csv");
    }
}
