package com.flatmeals.account;

import com.flatmeals.account.LedgerDtos.AccountTransaction;
import com.flatmeals.account.LedgerDtos.AccountsResponse;
import com.flatmeals.account.LedgerDtos.CashHolderPosition;
import com.flatmeals.account.LedgerDtos.CashReportResponse;
import com.flatmeals.account.LedgerDtos.MemberAccount;
import com.flatmeals.account.LedgerDtos.ReconciliationRow;
import com.flatmeals.calc.BalanceCalculator;
import com.flatmeals.calc.CashCalculator;
import com.flatmeals.calc.CashMovement;
import com.flatmeals.calc.MemberMonthInput;
import com.flatmeals.calc.MoneyUtil;
import com.flatmeals.calc.ReconciliationLine;
import com.flatmeals.cash.CashAdjustment;
import com.flatmeals.cash.CashAdjustmentRepository;
import com.flatmeals.cash.CashCount;
import com.flatmeals.cash.CashCountRepository;
import com.flatmeals.cash.CashReceipt;
import com.flatmeals.cash.CashReceiptRepository;
import com.flatmeals.cash.CashTransfer;
import com.flatmeals.cash.CashTransferRepository;
import com.flatmeals.common.NotFoundException;
import com.flatmeals.expense.Expense;
import com.flatmeals.expense.ExpenseRepository;
import com.flatmeals.meal.MealDtos.MealSummaryResponse;
import com.flatmeals.meal.MealDtos.MemberMealCost;
import com.flatmeals.meal.MealService;
import com.flatmeals.member.Member;
import com.flatmeals.member.MemberRepository;
import com.flatmeals.month.FlatMonth;
import com.flatmeals.month.MonthRepository;
import com.flatmeals.month.MonthService;
import com.flatmeals.payment.Adjustment;
import com.flatmeals.payment.AdjustmentRepository;
import com.flatmeals.payment.AdjustmentType;
import com.flatmeals.payment.Payment;
import com.flatmeals.payment.PaymentRepository;
import com.flatmeals.payment.PaymentType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Works out every member's personal account and every holder's cash for a month.
 *
 * Carry-forward: months are calculated in order, oldest first. Each month's
 * closing balances and closing cash become the next month's opening figures.
 * Opening balances are never stored as transactions, so nothing is counted twice.
 * Closed months are read from their frozen snapshots, so they never change.
 *
 * All the arithmetic is done by the tested calculators in the calc package.
 */
@Service
public class LedgerService {

    private static final BigDecimal ZERO = MoneyUtil.zero();
    private static final ZoneId FLAT_ZONE = ZoneId.of("Asia/Dhaka");

    private final MonthService monthService;
    private final MonthRepository monthRepository;
    private final MealService mealService;
    private final ExpenseRepository expenseRepository;
    private final PaymentRepository paymentRepository;
    private final AdjustmentRepository adjustmentRepository;
    private final CashTransferRepository transferRepository;
    private final CashReceiptRepository receiptRepository;
    private final CashCountRepository countRepository;
    private final CashAdjustmentRepository cashAdjustmentRepository;
    private final MemberRepository memberRepository;
    private final MonthMemberSummaryRepository memberSummaryRepository;
    private final MonthCashSummaryRepository cashSummaryRepository;

    public LedgerService(MonthService monthService, MonthRepository monthRepository, MealService mealService,
                         ExpenseRepository expenseRepository, PaymentRepository paymentRepository,
                         AdjustmentRepository adjustmentRepository, CashTransferRepository transferRepository,
                         CashReceiptRepository receiptRepository, CashCountRepository countRepository,
                         CashAdjustmentRepository cashAdjustmentRepository, MemberRepository memberRepository,
                         MonthMemberSummaryRepository memberSummaryRepository,
                         MonthCashSummaryRepository cashSummaryRepository) {
        this.memberSummaryRepository = memberSummaryRepository;
        this.cashSummaryRepository = cashSummaryRepository;
        this.monthService = monthService;
        this.monthRepository = monthRepository;
        this.mealService = mealService;
        this.expenseRepository = expenseRepository;
        this.paymentRepository = paymentRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.transferRepository = transferRepository;
        this.receiptRepository = receiptRepository;
        this.countRepository = countRepository;
        this.cashAdjustmentRepository = cashAdjustmentRepository;
        this.memberRepository = memberRepository;
    }

    // ------------------------------------------------------------ public API

    @Transactional(readOnly = true)
    public AccountsResponse accounts(int year, int month) {
        MonthLedger ledger = ledgerFor(year, month);
        MealSummaryResponse meals = ledger.meals();
        BigDecimal totalOwed = ZERO;
        BigDecimal totalCredit = ZERO;
        for (MemberAccount account : ledger.accounts()) {
            if (account.closingBalance().signum() > 0) {
                totalOwed = totalOwed.add(account.closingBalance());
            } else {
                totalCredit = totalCredit.add(account.closingBalance().negate());
            }
        }
        FlatMonth m = ledger.month();
        return new AccountsResponse(m.getYear(), m.getMonthNumber(), m.label(), m.getStatus(),
                meals.totalMeals(), meals.totalExpense(), meals.mealRate(), meals.warning(),
                ledger.accounts(), totalOwed, totalCredit);
    }

    @Transactional(readOnly = true)
    public MemberAccount account(int year, int month, Long memberId) {
        MonthLedger ledger = ledgerFor(year, month);
        return ledger.accounts().stream()
                .filter(account -> account.memberId().equals(memberId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No account activity for member " + memberId
                        + " in " + ledger.month().label()));
    }

    @Transactional(readOnly = true)
    public CashReportResponse cashReport(int year, int month) {
        MonthLedger ledger = ledgerFor(year, month);
        BigDecimal totalCash = ZERO;
        for (CashHolderPosition holder : ledger.cashHolders()) {
            totalCash = totalCash.add(holder.closingCash());
        }
        FlatMonth m = ledger.month();
        return new CashReportResponse(m.getYear(), m.getMonthNumber(), m.label(), m.getStatus(),
                ledger.cashHolders(), totalCash, ledger.reconciliation());
    }

    /**
     * Calculates the requested month, carrying balances and cash forward from
     * the months before it.
     *
     * Earlier CLOSED months are not recalculated: their frozen closing figures
     * (written by MonthClosingService) are used directly. Earlier OPEN months
     * are calculated live.
     */
    @Transactional(readOnly = true)
    public MonthLedger ledgerFor(int year, int month) {
        FlatMonth target = monthService.find(year, month);
        Map<Long, BigDecimal> balances = new HashMap<>();
        Map<Long, BigDecimal> cash = new HashMap<>();
        MonthLedger ledger = null;
        for (FlatMonth flatMonth : monthRepository.findAllByOrderByYearAscMonthNumberAsc()) {
            if (flatMonth.yearMonth().isAfter(target.yearMonth())) {
                break;
            }
            boolean isTarget = flatMonth.yearMonth().equals(target.yearMonth());
            if (!isTarget && !flatMonth.isOpen()) {
                balances = frozenBalances(flatMonth);
                cash = frozenCash(flatMonth);
                continue;
            }
            ledger = computeMonth(flatMonth, balances, cash);
            balances = ledger.closingBalances();
            cash = ledger.closingCash();
        }
        if (ledger == null) {
            throw new NotFoundException(target.label() + " could not be calculated");
        }
        return ledger;
    }

    private Map<Long, BigDecimal> frozenBalances(FlatMonth closedMonth) {
        Map<Long, BigDecimal> balances = new HashMap<>();
        for (MonthMemberSummary row : memberSummaryRepository.findByMonthId(closedMonth.getId())) {
            balances.put(row.getMemberId(), row.getClosingBalance().setScale(2));
        }
        return balances;
    }

    private Map<Long, BigDecimal> frozenCash(FlatMonth closedMonth) {
        Map<Long, BigDecimal> cash = new HashMap<>();
        for (MonthCashSummary row : cashSummaryRepository.findByMonthId(closedMonth.getId())) {
            cash.put(row.getHolderMemberId(), row.getClosingCash().setScale(2));
        }
        return cash;
    }

    // ------------------------------------------------------- one month's maths

    private MonthLedger computeMonth(FlatMonth flatMonth, Map<Long, BigDecimal> openingBalances,
                                     Map<Long, BigDecimal> openingCash) {
        Long monthId = flatMonth.getId();
        MealSummaryResponse meals = mealService.getSummary(flatMonth.getYear(), flatMonth.getMonthNumber());
        List<Expense> expenses = expenseRepository.findByMonthIdOrderByExpenseDateAscIdAsc(monthId);
        List<Payment> payments = paymentRepository.findByMonthIdOrderByPaidOnAscIdAsc(monthId);
        List<Adjustment> adjustments = adjustmentRepository.findByMonthIdOrderByIdAsc(monthId);
        List<CashTransfer> transfers = transferRepository.findByMonthIdOrderByTransferredOnAscIdAsc(monthId);
        List<CashReceipt> receipts = receiptRepository.findByMonthIdOrderByReceivedOnAscIdAsc(monthId);
        List<CashAdjustment> cashAdjustments = cashAdjustmentRepository.findByMonthIdOrderByIdAsc(monthId);
        List<CashCount> counts = countRepository.findByMonthIdOrderByIdAsc(monthId);

        Map<Long, String> names = memberNames(meals, openingBalances, openingCash, expenses, payments,
                adjustments, transfers, receipts, cashAdjustments, counts);

        List<MemberAccount> accounts = buildAccounts(flatMonth, meals, openingBalances, expenses,
                payments, adjustments, names);

        // ---- Cash: every movement goes FROM someone TO someone (see CashMovement)
        List<CashMovement> movements = new ArrayList<>();
        for (Payment p : payments) {
            if (p.isActive()) {
                movements.add(p.getType() == PaymentType.PAYMENT
                        ? CashMovement.paymentReceived(p.getCashHolderId(), p.getAmount())
                        : CashMovement.refundPaid(p.getCashHolderId(), p.getAmount()));
            }
        }
        for (Expense e : expenses) {
            if (e.isActive() && !e.isPaidFromPocket()) {   // out-of-pocket expenses never touch flat cash
                movements.add(CashMovement.expensePaid(e.getHolderMemberId(), e.getAmount()));
            }
        }
        for (CashTransfer t : transfers) {
            if (t.isActive()) {
                movements.add(CashMovement.transfer(t.getFromMemberId(), t.getToMemberId(), t.getAmount()));
            }
        }
        for (CashReceipt r : receipts) {
            if (r.isActive()) {
                movements.add(CashMovement.otherReceipt(r.getHolderMemberId(), r.getAmount()));
            }
        }
        for (CashAdjustment a : cashAdjustments) {
            if (a.isActive()) {
                movements.add(CashMovement.adjustment(a.getHolderMemberId(), a.getAmount()));
            }
        }

        Map<Long, BigDecimal> closingCash = CashCalculator.closingCash(openingCash, movements);
        List<CashHolderPosition> holders = buildCashPositions(openingCash, movements, closingCash, names);

        // Latest count per holder (counts are oldest first, so later ones overwrite)
        Map<Long, BigDecimal> latestCounts = new LinkedHashMap<>();
        for (CashCount count : counts) {
            latestCounts.put(count.getHolderMemberId(), count.getCountedAmount());
        }
        List<ReconciliationRow> reconciliation = new ArrayList<>();
        for (ReconciliationLine line : CashCalculator.reconcile(closingCash, latestCounts)) {
            reconciliation.add(new ReconciliationRow(line.holderId(), names.get(line.holderId()),
                    line.recorded(), line.counted(), line.difference(), line.status()));
        }
        reconciliation.sort(Comparator.comparing(ReconciliationRow::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

        return new MonthLedger(flatMonth, meals, accounts, holders, reconciliation);
    }

    private List<MemberAccount> buildAccounts(FlatMonth flatMonth, MealSummaryResponse meals,
                                              Map<Long, BigDecimal> openingBalances, List<Expense> expenses,
                                              List<Payment> payments, List<Adjustment> adjustments,
                                              Map<Long, String> names) {
        // Everyone with something to show: this month's members, anyone carrying a
        // balance, and anyone with a payment, adjustment or out-of-pocket expense.
        Map<Long, MemberMealCost> mealRows = new LinkedHashMap<>();
        for (MemberMealCost row : meals.members()) {
            mealRows.put(row.memberId(), row);
        }
        Set<Long> memberIds = new LinkedHashSet<>(mealRows.keySet());
        openingBalances.forEach((id, balance) -> {
            if (balance.signum() != 0) {
                memberIds.add(id);
            }
        });
        payments.forEach(p -> memberIds.add(p.getMemberId()));
        adjustments.forEach(a -> memberIds.add(a.getMemberId()));
        expenses.stream().filter(Expense::isPaidFromPocket).forEach(e -> memberIds.add(e.getHolderMemberId()));

        List<MemberAccount> accounts = new ArrayList<>();
        for (Long memberId : memberIds) {
            accounts.add(buildAccount(flatMonth, memberId, names, openingBalances.getOrDefault(memberId, ZERO),
                    mealRows.get(memberId), meals.mealRate(), expenses, payments, adjustments));
        }
        accounts.sort(Comparator.comparing(MemberAccount::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return accounts;
    }

    private MemberAccount buildAccount(FlatMonth flatMonth, Long memberId, Map<Long, String> names,
                                       BigDecimal opening, MemberMealCost mealRow, BigDecimal mealRate,
                                       List<Expense> expenses, List<Payment> payments, List<Adjustment> adjustments) {
        LocalDate firstDay = flatMonth.yearMonth().atDay(1);
        LocalDate lastDay = flatMonth.yearMonth().atEndOfMonth();
        List<AccountTransaction> history = new ArrayList<>();

        if (opening.signum() != 0) {
            history.add(new AccountTransaction(firstDay, "OPENING_BALANCE",
                    "Balance carried from the previous month", opening, false, null));
        }

        BigDecimal mealCount = ZERO;
        BigDecimal mealCost = ZERO;
        if (mealRow != null) {
            mealCount = mealRow.meals();
            mealCost = (mealRow.cost() == null) ? ZERO : mealRow.cost();
            String rateText = (mealRate == null) ? "" : " × rate " + mealRate.stripTrailingZeros().toPlainString();
            history.add(new AccountTransaction(lastDay, "MEAL_COST",
                    mealCount.toPlainString() + " meals" + rateText, mealCost, false, null));
        }

        BigDecimal paid = ZERO;
        BigDecimal refunded = ZERO;
        for (Payment p : payments) {
            if (!p.getMemberId().equals(memberId)) {
                continue;
            }
            String holder = names.getOrDefault(p.getCashHolderId(), "member " + p.getCashHolderId());
            if (p.getType() == PaymentType.PAYMENT) {
                if (p.isActive()) {
                    paid = paid.add(p.getAmount());
                }
                history.add(new AccountTransaction(p.getPaidOn(), "PAYMENT", "Paid to " + holder,
                        p.getAmount().negate(), !p.isActive(), p.getVoidReason()));
            } else {
                if (p.isActive()) {
                    refunded = refunded.add(p.getAmount());
                }
                history.add(new AccountTransaction(p.getPaidOn(), "REFUND", "Refund from " + holder,
                        p.getAmount(), !p.isActive(), p.getVoidReason()));
            }
        }

        BigDecimal adjusted = ZERO;
        for (Adjustment a : adjustments) {
            if (!a.getMemberId().equals(memberId)) {
                continue;
            }
            if (a.isActive()) {
                adjusted = adjusted.add(a.getAmount());
            }
            LocalDate date = (a.getType() == AdjustmentType.OPENING || a.getCreatedAt() == null)
                    ? firstDay
                    : LocalDate.ofInstant(a.getCreatedAt(), FLAT_ZONE);
            history.add(new AccountTransaction(date, "ADJUSTMENT",
                    a.getType().name().charAt(0) + a.getType().name().substring(1).toLowerCase() + ": " + a.getReason(),
                    a.getAmount(), !a.isActive(), a.getVoidReason()));
        }

        BigDecimal pocket = ZERO;
        for (Expense e : expenses) {
            if (!e.isPaidFromPocket() || !e.getHolderMemberId().equals(memberId)) {
                continue;
            }
            if (e.isActive()) {
                pocket = pocket.add(e.getAmount());
            }
            history.add(new AccountTransaction(e.getExpenseDate(), "POCKET_EXPENSE",
                    "Paid from own pocket: " + e.getCategory(), e.getAmount().negate(), !e.isActive(), e.getVoidReason()));
        }

        BigDecimal closing = BalanceCalculator.closingBalance(
                new MemberMonthInput(opening, mealCost, paid, refunded, adjusted, pocket));
        history.sort(Comparator.comparing(AccountTransaction::date));   // stable: same-day order is kept

        String status = closing.signum() > 0 ? "OWES" : (closing.signum() < 0 ? "CREDIT" : "SETTLED");
        return new MemberAccount(memberId, names.getOrDefault(memberId, "Member " + memberId), opening,
                mealCount, mealCost, paid, refunded, adjusted, pocket, closing, status, history);
    }

    private List<CashHolderPosition> buildCashPositions(Map<Long, BigDecimal> openingCash, List<CashMovement> movements,
                                                        Map<Long, BigDecimal> closingCash, Map<Long, String> names) {
        Map<Long, BigDecimal> cashIn = new HashMap<>();
        Map<Long, BigDecimal> cashOut = new HashMap<>();
        for (CashMovement movement : movements) {
            if (movement.toHolderId() != null) {
                cashIn.merge(movement.toHolderId(), movement.amount(), BigDecimal::add);
            }
            if (movement.fromHolderId() != null) {
                cashOut.merge(movement.fromHolderId(), movement.amount(), BigDecimal::add);
            }
        }
        List<CashHolderPosition> holders = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : closingCash.entrySet()) {
            Long id = entry.getKey();
            holders.add(new CashHolderPosition(id, names.getOrDefault(id, "Member " + id),
                    openingCash.getOrDefault(id, ZERO).setScale(2),
                    cashIn.getOrDefault(id, ZERO).setScale(2),
                    cashOut.getOrDefault(id, ZERO).setScale(2),
                    entry.getValue()));
        }
        holders.sort(Comparator.comparing(CashHolderPosition::name, String.CASE_INSENSITIVE_ORDER));
        return holders;
    }

    private Map<Long, String> memberNames(MealSummaryResponse meals, Map<Long, BigDecimal> openingBalances,
                                          Map<Long, BigDecimal> openingCash, List<Expense> expenses,
                                          List<Payment> payments, List<Adjustment> adjustments,
                                          List<CashTransfer> transfers, List<CashReceipt> receipts,
                                          List<CashAdjustment> cashAdjustments, List<CashCount> counts) {
        Set<Long> ids = new HashSet<>();
        meals.members().forEach(m -> ids.add(m.memberId()));
        ids.addAll(openingBalances.keySet());
        ids.addAll(openingCash.keySet());
        expenses.forEach(e -> ids.add(e.getHolderMemberId()));
        payments.forEach(p -> {
            ids.add(p.getMemberId());
            ids.add(p.getCashHolderId());
        });
        adjustments.forEach(a -> ids.add(a.getMemberId()));
        transfers.forEach(t -> {
            ids.add(t.getFromMemberId());
            ids.add(t.getToMemberId());
        });
        receipts.forEach(r -> ids.add(r.getHolderMemberId()));
        cashAdjustments.forEach(a -> ids.add(a.getHolderMemberId()));
        counts.forEach(c -> ids.add(c.getHolderMemberId()));

        Map<Long, String> names = new HashMap<>();
        for (Member member : memberRepository.findAllById(ids)) {
            names.put(member.getId(), member.getName());
        }
        return names;
    }
}
