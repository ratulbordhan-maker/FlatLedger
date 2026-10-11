package com.flatmeals.account;

import com.flatmeals.account.LedgerDtos.AccountTransaction;
import com.flatmeals.account.LedgerDtos.AccountsResponse;
import com.flatmeals.account.LedgerDtos.CashHolderPosition;
import com.flatmeals.account.LedgerDtos.CashReportResponse;
import com.flatmeals.account.LedgerDtos.MemberAccount;
import com.flatmeals.account.LedgerDtos.ReconciliationRow;
import com.flatmeals.calc.ReconciliationLine;
import com.flatmeals.cash.CashAdjustment;
import com.flatmeals.cash.CashAdjustmentRepository;
import com.flatmeals.cash.CashAdjustmentType;
import com.flatmeals.cash.CashCount;
import com.flatmeals.cash.CashCountRepository;
import com.flatmeals.cash.CashReceiptRepository;
import com.flatmeals.cash.CashTransfer;
import com.flatmeals.cash.CashTransferRepository;
import com.flatmeals.expense.Expense;
import com.flatmeals.expense.ExpenseRepository;
import com.flatmeals.expense.FundingSource;
import com.flatmeals.meal.MealDtos.MealSummaryResponse;
import com.flatmeals.meal.MealDtos.MemberMealCost;
import com.flatmeals.meal.MealService;
import com.flatmeals.member.Member;
import com.flatmeals.member.MemberRepository;
import com.flatmeals.month.FlatMonth;
import com.flatmeals.month.MonthRepository;
import com.flatmeals.month.MonthService;
import com.flatmeals.month.MonthStatus;
import com.flatmeals.payment.Adjustment;
import com.flatmeals.payment.AdjustmentRepository;
import com.flatmeals.payment.AdjustmentType;
import com.flatmeals.payment.Payment;
import com.flatmeals.payment.PaymentRepository;
import com.flatmeals.payment.PaymentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The worked example from the design, run through the REAL ledger code.
 * Only the database is faked (Mockito); all the money logic is real.
 *
 * October: A (manager, id 1), B (id 2), C (id 3).
 *   Opening dues from the spreadsheet: A +300, B -200 (OPENING adjustments).
 *   Opening cash: A holds 1000 (OPENING cash adjustment).
 *   Expenses: A 9000 flat cash, B 1500 flat cash, C 2000 from own pocket.
 *   Payments: A pays 5000 (A keeps it), B pays 4500 to A, C pays 2000 to B.
 *   B hands 300 to A. A counts 1750 at the end.
 * November: nothing happens yet, so everything carries forward.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LedgerServiceTest {

    private static final Long A = 1L;
    private static final Long B = 2L;
    private static final Long C = 3L;
    private static final Long OCT = 10L;
    private static final Long NOV = 11L;

    @Mock private MonthService monthService;
    @Mock private MonthRepository monthRepository;
    @Mock private MealService mealService;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private AdjustmentRepository adjustmentRepository;
    @Mock private CashTransferRepository transferRepository;
    @Mock private CashReceiptRepository receiptRepository;
    @Mock private CashCountRepository countRepository;
    @Mock private CashAdjustmentRepository cashAdjustmentRepository;
    @Mock private MemberRepository memberRepository;

    private LedgerService ledgerService;

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private static LocalDate oct(int day) {
        return LocalDate.of(2026, 10, day);
    }

    @BeforeEach
    void setUp() {
        ledgerService = new LedgerService(monthService, monthRepository, mealService, expenseRepository,
                paymentRepository, adjustmentRepository, transferRepository, receiptRepository,
                countRepository, cashAdjustmentRepository, memberRepository);

        FlatMonth october = withId(new FlatMonth(2026, 10), OCT);
        FlatMonth november = withId(new FlatMonth(2026, 11), NOV);
        when(monthService.find(2026, 10)).thenReturn(october);
        when(monthService.find(2026, 11)).thenReturn(november);
        when(monthRepository.findAllByOrderByYearAscMonthNumberAsc()).thenReturn(List.of(october, november));
        when(memberRepository.findAllById(any())).thenReturn(List.of(
                withId(new Member("A", oct(1)), A), withId(new Member("B", oct(1)), B), withId(new Member("C", oct(1)), C)));

        // October meals: costs from the tested engine (5524.86 + 4189.69 + 2785.45 = 12500.00)
        when(mealService.getSummary(2026, 10)).thenReturn(new MealSummaryResponse(2026, 10, "October 2026",
                MonthStatus.OPEN, bd("135.75"), bd("12500.00"), bd("92.081031"), true, null, List.of(
                new MemberMealCost(A, "A", bd("60.00"), bd("5524.86")),
                new MemberMealCost(B, "B", bd("45.50"), bd("4189.69")),
                new MemberMealCost(C, "C", bd("30.25"), bd("2785.45")))));
        // November: everyone is in the month but nobody has eaten yet
        when(mealService.getSummary(2026, 11)).thenReturn(new MealSummaryResponse(2026, 11, "November 2026",
                MonthStatus.OPEN, bd("0.00"), bd("0.00"), null, false, "No meals", List.of(
                new MemberMealCost(A, "A", bd("0.00"), bd("0.00")),
                new MemberMealCost(B, "B", bd("0.00"), bd("0.00")),
                new MemberMealCost(C, "C", bd("0.00"), bd("0.00")))));

        when(expenseRepository.findByMonthIdOrderByExpenseDateAscIdAsc(OCT)).thenReturn(List.of(
                new Expense(OCT, 1L, oct(5), "Groceries", null, bd("9000.00"), true, FundingSource.FLAT_CASH, A),
                new Expense(OCT, 1L, oct(12), "Groceries", null, bd("1500.00"), true, FundingSource.FLAT_CASH, B),
                new Expense(OCT, 1L, oct(18), "Fish", null, bd("2000.00"), true, FundingSource.MEMBER_POCKET, C)));

        Payment mistake = new Payment(OCT, 1L, C, B, bd("999.00"), PaymentType.PAYMENT, oct(15), null);
        mistake.voidRecord("Typed the wrong amount");
        when(paymentRepository.findByMonthIdOrderByPaidOnAscIdAsc(OCT)).thenReturn(List.of(
                new Payment(OCT, 1L, A, A, bd("5000.00"), PaymentType.PAYMENT, oct(10), null),
                new Payment(OCT, 1L, B, A, bd("4500.00"), PaymentType.PAYMENT, oct(10), null),
                mistake,
                new Payment(OCT, 1L, C, B, bd("2000.00"), PaymentType.PAYMENT, oct(15), null)));

        when(adjustmentRepository.findByMonthIdOrderByIdAsc(OCT)).thenReturn(List.of(
                new Adjustment(OCT, 1L, A, bd("300.00"), AdjustmentType.OPENING, "Due from spreadsheet"),
                new Adjustment(OCT, 1L, B, bd("-200.00"), AdjustmentType.OPENING, "Credit from spreadsheet")));

        when(transferRepository.findByMonthIdOrderByTransferredOnAscIdAsc(OCT)).thenReturn(List.of(
                new CashTransfer(OCT, 1L, B, A, bd("300.00"), oct(20), null)));

        when(cashAdjustmentRepository.findByMonthIdOrderByIdAsc(OCT)).thenReturn(List.of(
                new CashAdjustment(OCT, 1L, A, bd("1000.00"), CashAdjustmentType.OPENING, "Cash in hand at start")));

        when(countRepository.findByMonthIdOrderByIdAsc(OCT)).thenReturn(List.of(
                new CashCount(OCT, A, bd("1800.00"), "first count", 1L),
                new CashCount(OCT, A, bd("1750.00"), "recount", 1L)));   // the latest count wins
    }

    private static MemberAccount accountOf(AccountsResponse response, Long memberId) {
        return response.accounts().stream().filter(a -> a.memberId().equals(memberId)).findFirst().orElseThrow();
    }

    @Test
    void octoberBalancesMatchTheWorkedExample() {
        AccountsResponse october = ledgerService.accounts(2026, 10);

        assertThat(accountOf(october, A).closingBalance()).isEqualByComparingTo("824.86");
        assertThat(accountOf(october, A).balanceStatus()).isEqualTo("OWES");
        assertThat(accountOf(october, B).closingBalance()).isEqualByComparingTo("-510.31");
        assertThat(accountOf(october, B).balanceStatus()).isEqualTo("CREDIT");
        assertThat(accountOf(october, C).closingBalance()).isEqualByComparingTo("-1214.55");
        assertThat(october.totalOwed()).isEqualByComparingTo("824.86");
        assertThat(october.totalCredit()).isEqualByComparingTo("1724.86");
    }

    @Test
    void outOfPocketExpenseIsCreditedAndVoidedPaymentIsShownButNotCounted() {
        MemberAccount c = ledgerService.account(2026, 10, C);

        assertThat(c.pocketCredit()).isEqualByComparingTo("2000.00");
        assertThat(c.payments()).isEqualByComparingTo("2000.00");   // the voided 999 is not counted
        List<AccountTransaction> voided = c.transactions().stream().filter(AccountTransaction::voided).toList();
        assertThat(voided).hasSize(1);
        assertThat(voided.get(0).voidReason()).isEqualTo("Typed the wrong amount");
    }

    @Test
    void holdingFlatCashDoesNotChangeAPersonalBalance() {
        // B holds 200 of the flat's cash, yet her personal credit is exactly what she paid minus what she ate
        CashReportResponse cash = ledgerService.cashReport(2026, 10);
        MemberAccount b = ledgerService.account(2026, 10, B);

        CashHolderPosition bCash = cash.holders().stream().filter(h -> h.memberId().equals(B)).findFirst().orElseThrow();
        assertThat(bCash.closingCash()).isEqualByComparingTo("200.00");
        assertThat(b.closingBalance()).isEqualByComparingTo("-510.31");
    }

    @Test
    void cashPerHolderAndReconciliation() {
        CashReportResponse cash = ledgerService.cashReport(2026, 10);

        CashHolderPosition a = cash.holders().stream().filter(h -> h.memberId().equals(A)).findFirst().orElseThrow();
        assertThat(a.closingCash()).isEqualByComparingTo("1800.00");
        assertThat(a.cashIn()).isEqualByComparingTo("10800.00");   // 1000 opening adj + 5000 + 4500 + 300 transfer
        assertThat(a.cashOut()).isEqualByComparingTo("9000.00");
        assertThat(cash.totalCash()).isEqualByComparingTo("2000.00");

        ReconciliationRow aRow = cash.reconciliation().stream().filter(r -> r.memberId().equals(A)).findFirst().orElseThrow();
        assertThat(aRow.counted()).isEqualByComparingTo("1750.00");
        assertThat(aRow.difference()).isEqualByComparingTo("-50.00");
        assertThat(aRow.status()).isEqualTo(ReconciliationLine.Status.SHORT);
        ReconciliationRow bRow = cash.reconciliation().stream().filter(r -> r.memberId().equals(B)).findFirst().orElseThrow();
        assertThat(bRow.status()).isEqualTo(ReconciliationLine.Status.NOT_COUNTED);
    }

    @Test
    void balancesAndCashCarryForwardIntoNovemberWithoutDuplicates() {
        AccountsResponse november = ledgerService.accounts(2026, 11);

        MemberAccount a = accountOf(november, A);
        assertThat(a.openingBalance()).isEqualByComparingTo("824.86");
        assertThat(a.closingBalance()).isEqualByComparingTo("824.86");   // nothing new happened
        assertThat(a.payments()).isEqualByComparingTo("0.00");           // October's payments are not repeated
        assertThat(a.transactions()).extracting(AccountTransaction::type).contains("OPENING_BALANCE");
        assertThat(accountOf(november, B).openingBalance()).isEqualByComparingTo("-510.31");
        assertThat(accountOf(november, C).openingBalance()).isEqualByComparingTo("-1214.55");

        CashReportResponse novemberCash = ledgerService.cashReport(2026, 11);
        assertThat(novemberCash.totalCash()).isEqualByComparingTo("2000.00");
    }

    @Test
    void cashPlusMemberBalancesReconciles() {
        // Everything that came in from outside in October: opening dues (+300 -200) and opening cash (1000).
        // After a normal meal month, flat cash + all member balances must still equal that total.
        AccountsResponse accounts = ledgerService.accounts(2026, 10);
        CashReportResponse cash = ledgerService.cashReport(2026, 10);

        BigDecimal balances = accounts.accounts().stream().map(MemberAccount::closingBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(cash.totalCash().add(balances)).isEqualByComparingTo("1100.00");
    }
}
