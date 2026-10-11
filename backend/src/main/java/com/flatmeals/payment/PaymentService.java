package com.flatmeals.payment;

import com.flatmeals.audit.AuditAction;
import com.flatmeals.audit.AuditService;
import com.flatmeals.calc.MoneyUtil;
import com.flatmeals.common.ConflictException;
import com.flatmeals.common.NotFoundException;
import com.flatmeals.common.RecordStatus;
import com.flatmeals.member.MemberService;
import com.flatmeals.month.FlatMonth;
import com.flatmeals.month.MonthService;
import com.flatmeals.payment.PaymentDtos.AdjustmentRequest;
import com.flatmeals.payment.PaymentDtos.AdjustmentResponse;
import com.flatmeals.payment.PaymentDtos.PaymentRequest;
import com.flatmeals.payment.PaymentDtos.PaymentResponse;
import com.flatmeals.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Payments, refunds and balance adjustments.
 * To correct a mistake: void the wrong record (with a reason) and enter a new one.
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final AdjustmentRepository adjustmentRepository;
    private final MonthService monthService;
    private final MemberService memberService;
    private final AuditService auditService;

    public PaymentService(PaymentRepository paymentRepository, AdjustmentRepository adjustmentRepository,
                          MonthService monthService, MemberService memberService, AuditService auditService) {
        this.paymentRepository = paymentRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.monthService = monthService;
        this.memberService = memberService;
        this.auditService = auditService;
    }

    // ---------------------------------------------------------------- payments

    @Transactional(readOnly = true)
    public List<Payment> listPayments(int year, int month) {
        return paymentRepository.findByMonthIdOrderByPaidOnAscIdAsc(monthService.find(year, month).getId());
    }

    @Transactional
    public Payment createPayment(int year, int month, PaymentRequest request, CurrentUser actor) {
        FlatMonth flatMonth = monthService.findOpen(year, month);
        flatMonth.requireContains(request.paidOn(), "Payment date");
        memberService.get(request.memberId());
        memberService.get(request.cashHolderId());
        BigDecimal amount = MoneyUtil.requirePositiveMoney(request.amount(), "amount");
        PaymentType type = (request.type() == null) ? PaymentType.PAYMENT : request.type();

        boolean looksLikeDuplicate = paymentRepository.existsByMonthIdAndMemberIdAndPaidOnAndAmountAndTypeAndStatus(
                flatMonth.getId(), request.memberId(), request.paidOn(), amount, type, RecordStatus.ACTIVE);
        if (looksLikeDuplicate && !Boolean.TRUE.equals(request.allowDuplicate())) {
            throw new ConflictException("A " + type.name().toLowerCase() + " of " + amount
                    + " for this member on " + request.paidOn() + " already exists. If this really is a "
                    + "second one, send it again with \"allowDuplicate\": true.");
        }

        Payment payment = paymentRepository.save(new Payment(flatMonth.getId(), actor.userId(),
                request.memberId(), request.cashHolderId(), amount, type, request.paidOn(),
                blankToNull(request.note())));
        auditService.record(actor.userId(), "PAYMENT", payment.getId(), AuditAction.CREATE,
                null, PaymentResponse.from(payment), null);
        return payment;
    }

    @Transactional
    public Payment voidPayment(int year, int month, Long id, String reason, CurrentUser actor) {
        FlatMonth flatMonth = monthService.findOpen(year, month);
        Payment payment = paymentRepository.findByIdAndMonthId(id, flatMonth.getId())
                .orElseThrow(() -> new NotFoundException("Payment " + id + " not found in " + flatMonth.label()));
        PaymentResponse before = PaymentResponse.from(payment);
        payment.voidRecord(reason);
        auditService.record(actor.userId(), "PAYMENT", id, AuditAction.VOID,
                before, PaymentResponse.from(payment), reason);
        return payment;
    }

    // ------------------------------------------------------------- adjustments

    @Transactional(readOnly = true)
    public List<Adjustment> listAdjustments(int year, int month) {
        return adjustmentRepository.findByMonthIdOrderByIdAsc(monthService.find(year, month).getId());
    }

    @Transactional
    public Adjustment createAdjustment(int year, int month, AdjustmentRequest request, CurrentUser actor) {
        FlatMonth flatMonth = monthService.findOpen(year, month);
        memberService.get(request.memberId());
        BigDecimal amount = MoneyUtil.requireMoney(request.amount(), "amount");
        if (amount.signum() == 0) {
            throw new IllegalArgumentException("An adjustment cannot be 0");
        }
        AdjustmentType type = (request.type() == null) ? AdjustmentType.CORRECTION : request.type();

        Adjustment adjustment = adjustmentRepository.save(new Adjustment(flatMonth.getId(), actor.userId(),
                request.memberId(), amount, type, request.reason().trim()));
        auditService.record(actor.userId(), "ADJUSTMENT", adjustment.getId(), AuditAction.CREATE,
                null, AdjustmentResponse.from(adjustment), request.reason());
        return adjustment;
    }

    @Transactional
    public Adjustment voidAdjustment(int year, int month, Long id, String reason, CurrentUser actor) {
        FlatMonth flatMonth = monthService.findOpen(year, month);
        Adjustment adjustment = adjustmentRepository.findByIdAndMonthId(id, flatMonth.getId())
                .orElseThrow(() -> new NotFoundException("Adjustment " + id + " not found in " + flatMonth.label()));
        AdjustmentResponse before = AdjustmentResponse.from(adjustment);
        adjustment.voidRecord(reason);
        auditService.record(actor.userId(), "ADJUSTMENT", id, AuditAction.VOID,
                before, AdjustmentResponse.from(adjustment), reason);
        return adjustment;
    }

    private static String blankToNull(String text) {
        return (text == null || text.isBlank()) ? null : text.trim();
    }
}
