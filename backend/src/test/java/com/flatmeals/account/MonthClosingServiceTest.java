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
import com.flatmeals.month.MonthStatus;
import com.flatmeals.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthClosingServiceTest {

    private static final CurrentUser MANAGER = new CurrentUser(1L, 1L, "manager", true);

    @Mock private MonthService monthService;
    @Mock private MonthRepository monthRepository;
    @Mock private LedgerService ledgerService;
    @Mock private MonthMemberSummaryRepository memberSummaryRepository;
    @Mock private MonthCashSummaryRepository cashSummaryRepository;
    @Mock private AuditService auditService;

    @Captor private ArgumentCaptor<List<MonthMemberSummary>> memberRowsCaptor;
    @Captor private ArgumentCaptor<List<MonthCashSummary>> cashRowsCaptor;

    private MonthClosingService closingService;
    private FlatMonth october;

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static FlatMonth month(Long id, int year, int monthNumber) {
        FlatMonth flatMonth = new FlatMonth(year, monthNumber);
        ReflectionTestUtils.setField(flatMonth, "id", id);
        return flatMonth;
    }

    private static MealSummaryResponse meals(boolean canBeFinalized) {
        return new MealSummaryResponse(2026, 10, "October 2026", MonthStatus.OPEN,
                canBeFinalized ? bd("135.75") : bd("0.00"), bd("12500.00"),
                canBeFinalized ? bd("92.081031") : null, canBeFinalized,
                canBeFinalized ? null : "No meals recorded this month", List.of());
    }

    private MonthLedger ledger(boolean canBeFinalized) {
        MemberAccount anila = new MemberAccount(1L, "Anila", bd("0.00"), bd("60.00"), bd("5524.86"),
                bd("5000.00"), bd("100.00"), bd("300.00"), bd("0.00"), bd("924.86"), "OWES", List.of());
        CashHolderPosition anilaCash = new CashHolderPosition(1L, "Anila", bd("1000.00"), bd("9500.00"),
                bd("9000.00"), bd("1500.00"));
        return new MonthLedger(october, meals(canBeFinalized), List.of(anila), List.of(anilaCash), List.of());
    }

    @BeforeEach
    void setUp() {
        closingService = new MonthClosingService(monthService, monthRepository, ledgerService,
                memberSummaryRepository, cashSummaryRepository, auditService);
        october = month(10L, 2026, 10);
    }

    @Test
    void closingFreezesAccountsCashAndTotals() {
        when(monthService.find(2026, 10)).thenReturn(october);
        when(monthRepository.findByYearAndMonthNumber(2026, 9)).thenReturn(Optional.empty());
        when(ledgerService.ledgerFor(2026, 10)).thenReturn(ledger(true));

        FlatMonth closed = closingService.close(2026, 10, MANAGER);

        assertThat(closed.isOpen()).isFalse();
        assertThat(closed.getMealRate()).isEqualByComparingTo("92.081031");
        assertThat(closed.getTotalExpense()).isEqualByComparingTo("12500.00");
        assertThat(closed.getClosedBy()).isEqualTo(1L);
        assertThat(closed.getClosedAt()).isNotNull();

        verify(memberSummaryRepository).saveAll(memberRowsCaptor.capture());
        MonthMemberSummary row = memberRowsCaptor.getValue().get(0);
        assertThat(row.getClosingBalance()).isEqualByComparingTo("924.86");
        assertThat(row.getPaymentsTotal()).isEqualByComparingTo("4900.00");   // 5000 paid - 100 refunded

        verify(cashSummaryRepository).saveAll(cashRowsCaptor.capture());
        assertThat(cashRowsCaptor.getValue().get(0).getClosingCash()).isEqualByComparingTo("1500.00");

        verify(auditService).record(eq(1L), eq("MONTH"), eq(10L), eq(AuditAction.CLOSE), isNull(), any(), isNull());
    }

    @Test
    void previousMonthMustBeClosedFirst() {
        when(monthService.find(2026, 10)).thenReturn(october);
        when(monthRepository.findByYearAndMonthNumber(2026, 9)).thenReturn(Optional.of(month(9L, 2026, 9)));

        assertThatThrownBy(() -> closingService.close(2026, 10, MANAGER))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("September 2026");
        verifyNoInteractions(ledgerService, memberSummaryRepository);
        assertThat(october.isOpen()).isTrue();
    }

    @Test
    void monthWithNoMealsCannotBeClosed() {
        when(monthService.find(2026, 10)).thenReturn(october);
        when(monthRepository.findByYearAndMonthNumber(2026, 9)).thenReturn(Optional.empty());
        when(ledgerService.ledgerFor(2026, 10)).thenReturn(ledger(false));

        assertThatThrownBy(() -> closingService.close(2026, 10, MANAGER))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("No meals");
        verify(memberSummaryRepository, never()).saveAll(any());
        assertThat(october.isOpen()).isTrue();
    }

    @Test
    void closedMonthCannotBeClosedAgain() {
        october.close(bd("1"), bd("1"), bd("1"), 1L);
        when(monthService.find(2026, 10)).thenReturn(october);

        assertThatThrownBy(() -> closingService.close(2026, 10, MANAGER))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void reopeningUnlocksTheMonthAndRemovesTheSnapshots() {
        october.close(bd("135.75"), bd("12500.00"), bd("92.081031"), 1L);
        when(monthService.find(2026, 10)).thenReturn(october);
        when(monthRepository.findByYearAndMonthNumber(2026, 11)).thenReturn(Optional.of(month(11L, 2026, 11)));
        List<MonthMemberSummary> frozen = List.of(new MonthMemberSummary(10L, 1L, bd("0"), bd("60"),
                bd("5524.86"), bd("5000"), bd("300"), bd("0"), bd("824.86")));
        when(memberSummaryRepository.findByMonthId(10L)).thenReturn(frozen);
        when(cashSummaryRepository.findByMonthId(10L)).thenReturn(List.of());

        FlatMonth reopened = closingService.reopen(2026, 10, "  Forgot the gas cylinder expense ", MANAGER);

        assertThat(reopened.isOpen()).isTrue();
        assertThat(reopened.getMealRate()).isNull();
        verify(memberSummaryRepository).deleteAll(frozen);
        verify(auditService).record(eq(1L), eq("MONTH"), eq(10L), eq(AuditAction.REOPEN), any(), isNull(),
                eq("Forgot the gas cylinder expense"));
    }

    @Test
    void onlyTheLatestClosedMonthCanBeReopened() {
        october.close(bd("1"), bd("1"), bd("1"), 1L);
        FlatMonth november = month(11L, 2026, 11);
        november.close(bd("1"), bd("1"), bd("1"), 1L);
        when(monthService.find(2026, 10)).thenReturn(october);
        when(monthRepository.findByYearAndMonthNumber(2026, 11)).thenReturn(Optional.of(november));

        assertThatThrownBy(() -> closingService.reopen(2026, 10, "Fix", MANAGER))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("November 2026");
        assertThat(october.isOpen()).isFalse();
    }

    @Test
    void openMonthCannotBeReopened() {
        when(monthService.find(2026, 10)).thenReturn(october);

        assertThatThrownBy(() -> closingService.reopen(2026, 10, "Fix", MANAGER))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void reopeningNeedsAReason() {
        assertThatThrownBy(() -> closingService.reopen(2026, 10, "  ", MANAGER))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(monthService);
    }
}
