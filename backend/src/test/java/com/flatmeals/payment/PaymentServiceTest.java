package com.flatmeals.payment;

import com.flatmeals.audit.AuditService;
import com.flatmeals.common.ConflictException;
import com.flatmeals.common.RecordStatus;
import com.flatmeals.member.Member;
import com.flatmeals.member.MemberService;
import com.flatmeals.month.FlatMonth;
import com.flatmeals.month.MonthService;
import com.flatmeals.payment.PaymentDtos.AdjustmentRequest;
import com.flatmeals.payment.PaymentDtos.PaymentRequest;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final CurrentUser MANAGER = new CurrentUser(1L, 1L, "manager", true);
    private static final Long OCT = 10L;
    private static final LocalDate OCT_10 = LocalDate.of(2026, 10, 10);

    @Mock private PaymentRepository paymentRepository;
    @Mock private AdjustmentRepository adjustmentRepository;
    @Mock private MonthService monthService;
    @Mock private MemberService memberService;
    @Mock private AuditService auditService;

    private PaymentService paymentService;
    private FlatMonth october;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, adjustmentRepository, monthService,
                memberService, auditService);
        october = new FlatMonth(2026, 10);
        ReflectionTestUtils.setField(october, "id", OCT);
    }

    private void octoberIsOpenWithMembers() {
        when(monthService.findOpen(2026, 10)).thenReturn(october);
        when(memberService.get(any())).thenReturn(new Member("Someone", LocalDate.of(2026, 1, 1)));
    }

    @Test
    void paymentIsTheDefaultType() {
        octoberIsOpenWithMembers();
        when(paymentRepository.existsByMonthIdAndMemberIdAndPaidOnAndAmountAndTypeAndStatus(
                OCT, 2L, OCT_10, new BigDecimal("4500.00"), PaymentType.PAYMENT, RecordStatus.ACTIVE)).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(call -> call.getArgument(0));

        Payment payment = paymentService.createPayment(2026, 10,
                new PaymentRequest(2L, 1L, new BigDecimal("4500"), null, OCT_10, null, null), MANAGER);

        assertThat(payment.getType()).isEqualTo(PaymentType.PAYMENT);
        assertThat(payment.getMemberId()).isEqualTo(2L);
        assertThat(payment.getCashHolderId()).isEqualTo(1L);
    }

    @Test
    void sameMemberSameDaySameAmountIsTreatedAsADuplicate() {
        octoberIsOpenWithMembers();
        when(paymentRepository.existsByMonthIdAndMemberIdAndPaidOnAndAmountAndTypeAndStatus(
                OCT, 2L, OCT_10, new BigDecimal("4500.00"), PaymentType.PAYMENT, RecordStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> paymentService.createPayment(2026, 10,
                new PaymentRequest(2L, 1L, new BigDecimal("4500"), PaymentType.PAYMENT, OCT_10, null, null), MANAGER))
                .isInstanceOf(ConflictException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void paymentDateMustBeInTheMonth() {
        when(monthService.findOpen(2026, 10)).thenReturn(october);

        assertThatThrownBy(() -> paymentService.createPayment(2026, 10,
                new PaymentRequest(2L, 1L, new BigDecimal("100"), null, LocalDate.of(2026, 9, 30), null, null), MANAGER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void voidedPaymentStaysWithItsReason() {
        Payment payment = new Payment(OCT, 1L, 2L, 1L, new BigDecimal("4500.00"), PaymentType.PAYMENT, OCT_10, null);
        when(monthService.findOpen(2026, 10)).thenReturn(october);
        when(paymentRepository.findByIdAndMonthId(3L, OCT)).thenReturn(Optional.of(payment));

        paymentService.voidPayment(2026, 10, 3L, "Recorded for the wrong person", MANAGER);

        assertThat(payment.isActive()).isFalse();
        assertThat(payment.getVoidReason()).isEqualTo("Recorded for the wrong person");
    }

    @Test
    void zeroAdjustmentIsRefused() {
        octoberIsOpenWithMembers();

        assertThatThrownBy(() -> paymentService.createAdjustment(2026, 10,
                new AdjustmentRequest(2L, new BigDecimal("0.00"), null, "Nothing"), MANAGER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeAdjustmentGivesCreditAndDefaultsToCorrection() {
        octoberIsOpenWithMembers();
        when(adjustmentRepository.save(any(Adjustment.class))).thenAnswer(call -> call.getArgument(0));

        Adjustment adjustment = paymentService.createAdjustment(2026, 10,
                new AdjustmentRequest(2L, new BigDecimal("-150.00"), null, " Overcharged in September "), MANAGER);

        assertThat(adjustment.getAmount()).isEqualByComparingTo("-150.00");
        assertThat(adjustment.getType()).isEqualTo(AdjustmentType.CORRECTION);
        assertThat(adjustment.getReason()).isEqualTo("Overcharged in September");
    }
}
