package com.flatmeals.cash;

import com.flatmeals.audit.AuditService;
import com.flatmeals.cash.CashDtos.CashAdjustmentRequest;
import com.flatmeals.cash.CashDtos.CountRequest;
import com.flatmeals.cash.CashDtos.TransferRequest;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashServiceTest {

    private static final CurrentUser MANAGER = new CurrentUser(1L, 1L, "manager", true);
    private static final Long OCT = 10L;

    @Mock private CashTransferRepository transferRepository;
    @Mock private CashReceiptRepository receiptRepository;
    @Mock private CashCountRepository countRepository;
    @Mock private CashAdjustmentRepository cashAdjustmentRepository;
    @Mock private MonthService monthService;
    @Mock private MemberService memberService;
    @Mock private AuditService auditService;

    private CashService cashService;
    private FlatMonth october;

    @BeforeEach
    void setUp() {
        cashService = new CashService(transferRepository, receiptRepository, countRepository,
                cashAdjustmentRepository, monthService, memberService, auditService);
        october = new FlatMonth(2026, 10);
        ReflectionTestUtils.setField(october, "id", OCT);
    }

    @Test
    void transferBetweenTwoPeopleIsSaved() {
        when(monthService.findOpen(2026, 10)).thenReturn(october);
        when(memberService.get(any())).thenReturn(new Member("Someone", LocalDate.of(2026, 1, 1)));
        when(transferRepository.save(any(CashTransfer.class))).thenAnswer(call -> call.getArgument(0));

        CashTransfer transfer = cashService.createTransfer(2026, 10,
                new TransferRequest(2L, 1L, new BigDecimal("300"), LocalDate.of(2026, 10, 20), null), MANAGER);

        assertThat(transfer.getFromMemberId()).isEqualTo(2L);
        assertThat(transfer.getToMemberId()).isEqualTo(1L);
        assertThat(transfer.getAmount()).isEqualByComparingTo("300.00");
    }

    @Test
    void transferToTheSamePersonIsRefused() {
        when(monthService.findOpen(2026, 10)).thenReturn(october);

        assertThatThrownBy(() -> cashService.createTransfer(2026, 10,
                new TransferRequest(1L, 1L, new BigDecimal("300"), LocalDate.of(2026, 10, 20), null), MANAGER))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(transferRepository);
    }

    @Test
    void negativeCountIsRefused() {
        when(monthService.findOpen(2026, 10)).thenReturn(october);
        when(memberService.get(1L)).thenReturn(new Member("Anila", LocalDate.of(2026, 1, 1)));

        assertThatThrownBy(() -> cashService.createCount(2026, 10,
                new CountRequest(1L, new BigDecimal("-5"), null), MANAGER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroCashAdjustmentIsRefused() {
        when(monthService.findOpen(2026, 10)).thenReturn(october);
        when(memberService.get(1L)).thenReturn(new Member("Anila", LocalDate.of(2026, 1, 1)));

        assertThatThrownBy(() -> cashService.createCashAdjustment(2026, 10,
                new CashAdjustmentRequest(1L, new BigDecimal("0"), CashAdjustmentType.OTHER, "x"), MANAGER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void voidedTransferIsKeptWithItsReason() {
        CashTransfer transfer = new CashTransfer(OCT, 1L, 2L, 1L, new BigDecimal("300.00"), LocalDate.of(2026, 10, 20), null);
        when(monthService.findOpen(2026, 10)).thenReturn(october);
        when(transferRepository.findByIdAndMonthId(4L, OCT)).thenReturn(Optional.of(transfer));

        cashService.voidTransfer(2026, 10, 4L, "Never happened", MANAGER);

        assertThat(transfer.isActive()).isFalse();
        assertThat(transfer.getVoidReason()).isEqualTo("Never happened");
    }
}
