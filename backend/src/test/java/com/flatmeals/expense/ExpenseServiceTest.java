package com.flatmeals.expense;

import com.flatmeals.audit.AuditAction;
import com.flatmeals.audit.AuditService;
import com.flatmeals.common.ConflictException;
import com.flatmeals.common.RecordStatus;
import com.flatmeals.expense.ExpenseDtos.ExpenseRequest;
import com.flatmeals.member.Member;
import com.flatmeals.member.MemberService;
import com.flatmeals.month.FlatMonth;
import com.flatmeals.month.MonthService;
import com.flatmeals.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    private static final CurrentUser MANAGER = new CurrentUser(1L, 1L, "manager", true);
    private static final Long OCT = 10L;

    @Mock private ExpenseRepository expenseRepository;
    @Mock private MonthService monthService;
    @Mock private MemberService memberService;
    @Mock private AuditService auditService;

    private ExpenseService expenseService;
    private FlatMonth october;

    @BeforeEach
    void setUp() {
        expenseService = new ExpenseService(expenseRepository, monthService, memberService, auditService);
        october = new FlatMonth(2026, 10);
        ReflectionTestUtils.setField(october, "id", OCT);
    }

    private static ExpenseRequest groceries(String date, String amount, Boolean allowDuplicate) {
        return new ExpenseRequest(LocalDate.parse(date), " Groceries ", "Weekly market", new BigDecimal(amount),
                null, FundingSource.FLAT_CASH, 1L, allowDuplicate);
    }

    private void octoberIsOpenAndMemberExists() {
        when(monthService.findOpen(2026, 10)).thenReturn(october);
        when(memberService.get(1L)).thenReturn(new Member("Anila", LocalDate.of(2026, 1, 1)));
    }

    @Test
    void createsAnExpenseThatCountsTowardTheMealRateByDefault() {
        octoberIsOpenAndMemberExists();
        when(expenseRepository.existsByMonthIdAndExpenseDateAndAmountAndHolderMemberIdAndStatus(
                OCT, LocalDate.of(2026, 10, 5), new BigDecimal("9000.00"), 1L, RecordStatus.ACTIVE)).thenReturn(false);
        when(expenseRepository.save(any(Expense.class))).thenAnswer(call -> call.getArgument(0));

        Expense expense = expenseService.create(2026, 10, groceries("2026-10-05", "9000", null), MANAGER);

        assertThat(expense.getCategory()).isEqualTo("Groceries");   // trimmed
        assertThat(expense.getAmount()).isEqualByComparingTo("9000.00");
        assertThat(expense.isInMealRate()).isTrue();
        assertThat(expense.isActive()).isTrue();
        verify(auditService).record(eq(1L), eq("EXPENSE"), any(), eq(AuditAction.CREATE), any(), any(), any());
    }

    @Test
    void closedMonthIsRefused() {
        when(monthService.findOpen(2026, 9)).thenThrow(new ConflictException("September 2026 is closed"));

        assertThatThrownBy(() -> expenseService.create(2026, 9, groceries("2026-09-05", "100", null), MANAGER))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(expenseRepository);
    }

    @Test
    void dateOutsideTheMonthIsRefused() {
        when(monthService.findOpen(2026, 10)).thenReturn(october);

        assertThatThrownBy(() -> expenseService.create(2026, 10, groceries("2026-11-01", "100", null), MANAGER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("October 2026");
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void likelyDuplicateIsRefusedUnlessConfirmed() {
        octoberIsOpenAndMemberExists();
        when(expenseRepository.existsByMonthIdAndExpenseDateAndAmountAndHolderMemberIdAndStatus(
                OCT, LocalDate.of(2026, 10, 5), new BigDecimal("9000.00"), 1L, RecordStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> expenseService.create(2026, 10, groceries("2026-10-05", "9000", null), MANAGER))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("allowDuplicate");
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void confirmedDuplicateIsSaved() {
        octoberIsOpenAndMemberExists();
        when(expenseRepository.existsByMonthIdAndExpenseDateAndAmountAndHolderMemberIdAndStatus(
                OCT, LocalDate.of(2026, 10, 5), new BigDecimal("9000.00"), 1L, RecordStatus.ACTIVE)).thenReturn(true);
        when(expenseRepository.save(any(Expense.class))).thenAnswer(call -> call.getArgument(0));

        Expense expense = expenseService.create(2026, 10, groceries("2026-10-05", "9000", true), MANAGER);

        assertThat(expense.getAmount()).isEqualByComparingTo("9000.00");
    }

    @Test
    void voidingKeepsTheRecordWithItsReason() {
        Expense expense = new Expense(OCT, 1L, LocalDate.of(2026, 10, 5), "Groceries", null,
                new BigDecimal("500.00"), true, FundingSource.FLAT_CASH, 1L);
        when(monthService.findOpen(2026, 10)).thenReturn(october);
        when(expenseRepository.findByIdAndMonthId(7L, OCT)).thenReturn(Optional.of(expense));

        expenseService.voidExpense(2026, 10, 7L, "Entered twice", MANAGER);

        assertThat(expense.getStatus()).isEqualTo(RecordStatus.VOIDED);
        assertThat(expense.getVoidReason()).isEqualTo("Entered twice");
        verify(expenseRepository, never()).delete(any());
        verify(auditService).record(eq(1L), eq("EXPENSE"), eq(7L), eq(AuditAction.VOID), any(), any(), eq("Entered twice"));
    }

    @Test
    void voidedExpenseCannotBeVoidedAgainOrEdited() {
        Expense expense = new Expense(OCT, 1L, LocalDate.of(2026, 10, 5), "Groceries", null,
                new BigDecimal("500.00"), true, FundingSource.FLAT_CASH, 1L);
        expense.voidRecord("Entered twice");
        when(monthService.findOpen(2026, 10)).thenReturn(october);
        when(expenseRepository.findByIdAndMonthId(7L, OCT)).thenReturn(Optional.of(expense));

        assertThatThrownBy(() -> expenseService.voidExpense(2026, 10, 7L, "Again", MANAGER))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> expenseService.update(2026, 10, 7L, groceries("2026-10-05", "400", null), MANAGER))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void editChangesTheExpenseAndRecordsBeforeAndAfter() {
        Expense expense = new Expense(OCT, 1L, LocalDate.of(2026, 10, 5), "Groceries", null,
                new BigDecimal("500.00"), true, FundingSource.FLAT_CASH, 1L);
        octoberIsOpenAndMemberExists();
        when(expenseRepository.findByIdAndMonthId(7L, OCT)).thenReturn(Optional.of(expense));

        expenseService.update(2026, 10, 7L, groceries("2026-10-06", "450.50", null), MANAGER);

        assertThat(expense.getAmount()).isEqualByComparingTo("450.50");
        assertThat(expense.getExpenseDate()).isEqualTo(LocalDate.of(2026, 10, 6));
        verify(auditService).record(eq(1L), eq("EXPENSE"), eq(7L), eq(AuditAction.UPDATE), any(), any(), any());
    }
}
