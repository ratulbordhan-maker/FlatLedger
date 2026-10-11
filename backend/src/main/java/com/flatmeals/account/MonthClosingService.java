package com.flatmeals.account;

import com.flatmeals.account.LedgerDtos.CashHolderPosition;
import com.flatmeals.account.LedgerDtos.MemberAccount;
import com.flatmeals.audit.AuditAction;
import com.flatmeals.audit.AuditService;
import com.flatmeals.common.ConflictException;
import com.flatmeals.meal.MealDtos.MealSummaryResponse;
import com.flatmeals.month.FlatMonth;
import com.flatmeals.month.MonthRepository;
import com.flatmeals.month.MonthService;
import com.flatmeals.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Closing and reopening months.
 *
 * Closing (the end-of-month "finalise" step), all in ONE transaction:
 *   1. checks the month can be closed (see rules below);
 *   2. calculates the final accounts and cash with the same code as the live reports;
 *   3. saves them as frozen snapshots (month_member_summary, month_cash_summary);
 *   4. stores the month's totals and marks it CLOSED, which blocks every edit;
 *   5. writes an audit row.
 *
 * Rules:
 *   - months close in order: the previous month must be closed first;
 *   - a month with zero meals cannot be closed (there is no meal rate);
 *   - only the most recent closed month can be reopened, and only with a reason.
 */
@Service
public class MonthClosingService {

    private final MonthService monthService;
    private final MonthRepository monthRepository;
    private final LedgerService ledgerService;
    private final MonthMemberSummaryRepository memberSummaryRepository;
    private final MonthCashSummaryRepository cashSummaryRepository;
    private final AuditService auditService;

    public MonthClosingService(MonthService monthService, MonthRepository monthRepository,
                               LedgerService ledgerService, MonthMemberSummaryRepository memberSummaryRepository,
                               MonthCashSummaryRepository cashSummaryRepository, AuditService auditService) {
        this.monthService = monthService;
        this.monthRepository = monthRepository;
        this.ledgerService = ledgerService;
        this.memberSummaryRepository = memberSummaryRepository;
        this.cashSummaryRepository = cashSummaryRepository;
        this.auditService = auditService;
    }

    @Transactional
    public FlatMonth close(int year, int month, CurrentUser actor) {
        FlatMonth flatMonth = monthService.find(year, month);
        if (!flatMonth.isOpen()) {
            throw new ConflictException(flatMonth.label() + " is already closed");
        }
        YearMonth previous = flatMonth.yearMonth().minusMonths(1);
        monthRepository.findByYearAndMonthNumber(previous.getYear(), previous.getMonthValue())
                .filter(FlatMonth::isOpen)
                .ifPresent(open -> {
                    throw new ConflictException("Close " + open.label() + " first. Months are closed in order.");
                });

        MonthLedger ledger = ledgerService.ledgerFor(year, month);
        MealSummaryResponse meals = ledger.meals();
        if (!meals.canBeFinalized()) {
            throw new ConflictException(flatMonth.label() + " cannot be closed: " + meals.warning());
        }

        // Freeze every member's account
        List<MonthMemberSummary> memberRows = ledger.accounts().stream()
                .map(a -> new MonthMemberSummary(flatMonth.getId(), a.memberId(), a.openingBalance(), a.mealCount(),
                        a.mealCost(), a.payments().subtract(a.refunds()), a.adjustments(), a.pocketCredit(),
                        a.closingBalance()))
                .toList();
        memberSummaryRepository.saveAll(memberRows);

        // Freeze every holder's cash
        List<MonthCashSummary> cashRows = ledger.cashHolders().stream()
                .map(h -> new MonthCashSummary(flatMonth.getId(), h.memberId(), h.openingCash(), h.cashIn(),
                        h.cashOut(), h.closingCash()))
                .toList();
        cashSummaryRepository.saveAll(cashRows);

        flatMonth.close(meals.totalMeals(), meals.totalExpense(), meals.mealRate(), actor.userId());

        auditService.record(actor.userId(), "MONTH", flatMonth.getId(), AuditAction.CLOSE, null,
                totals(flatMonth, ledger.accounts(), ledger.cashHolders()), null);
        return flatMonth;
    }

    @Transactional
    public FlatMonth reopen(int year, int month, String reason, CurrentUser actor) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to reopen a month");
        }
        FlatMonth flatMonth = monthService.find(year, month);
        if (flatMonth.isOpen()) {
            throw new ConflictException(flatMonth.label() + " is not closed");
        }
        YearMonth next = flatMonth.yearMonth().plusMonths(1);
        monthRepository.findByYearAndMonthNumber(next.getYear(), next.getMonthValue())
                .filter(nextMonth -> !nextMonth.isOpen())
                .ifPresent(closed -> {
                    throw new ConflictException("Reopen " + closed.label() + " first. "
                            + "Only the most recent closed month can be reopened.");
                });

        // The audit row keeps the frozen figures, so nothing is lost when the snapshots go
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("totalMeals", flatMonth.getTotalMeals());
        before.put("totalExpense", flatMonth.getTotalExpense());
        before.put("mealRate", flatMonth.getMealRate());
        List<MonthMemberSummary> frozenAccounts = memberSummaryRepository.findByMonthId(flatMonth.getId());
        Map<Long, BigDecimal> frozenBalances = new LinkedHashMap<>();
        for (MonthMemberSummary row : frozenAccounts) {
            frozenBalances.put(row.getMemberId(), row.getClosingBalance());
        }
        before.put("closingBalances", frozenBalances);

        memberSummaryRepository.deleteAll(frozenAccounts);
        cashSummaryRepository.deleteAll(cashSummaryRepository.findByMonthId(flatMonth.getId()));
        flatMonth.reopen();

        auditService.record(actor.userId(), "MONTH", flatMonth.getId(), AuditAction.REOPEN, before, null, reason.trim());
        return flatMonth;
    }

    private Map<String, Object> totals(FlatMonth flatMonth, List<MemberAccount> accounts, List<CashHolderPosition> cash) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("month", flatMonth.label());
        values.put("totalMeals", flatMonth.getTotalMeals());
        values.put("totalExpense", flatMonth.getTotalExpense());
        values.put("mealRate", flatMonth.getMealRate());
        Map<Long, BigDecimal> balances = new LinkedHashMap<>();
        accounts.forEach(a -> balances.put(a.memberId(), a.closingBalance()));
        values.put("closingBalances", balances);
        Map<Long, BigDecimal> holders = new LinkedHashMap<>();
        cash.forEach(h -> holders.put(h.memberId(), h.closingCash()));
        values.put("closingCash", holders);
        return values;
    }
}
