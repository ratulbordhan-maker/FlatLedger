package com.flatmeals.cash;

import com.flatmeals.audit.AuditAction;
import com.flatmeals.audit.AuditService;
import com.flatmeals.calc.MoneyUtil;
import com.flatmeals.cash.CashDtos.CashAdjustmentRequest;
import com.flatmeals.cash.CashDtos.CashAdjustmentResponse;
import com.flatmeals.cash.CashDtos.CountRequest;
import com.flatmeals.cash.CashDtos.CountResponse;
import com.flatmeals.cash.CashDtos.ReceiptRequest;
import com.flatmeals.cash.CashDtos.ReceiptResponse;
import com.flatmeals.cash.CashDtos.TransferRequest;
import com.flatmeals.cash.CashDtos.TransferResponse;
import com.flatmeals.common.NotFoundException;
import com.flatmeals.member.MemberService;
import com.flatmeals.month.FlatMonth;
import com.flatmeals.month.MonthService;
import com.flatmeals.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Records about WHERE the flat's cash is. Nothing here changes anyone's
 * personal balance: holding flat cash is not the same as having credit.
 */
@Service
public class CashService {

    private final CashTransferRepository transferRepository;
    private final CashReceiptRepository receiptRepository;
    private final CashCountRepository countRepository;
    private final CashAdjustmentRepository cashAdjustmentRepository;
    private final MonthService monthService;
    private final MemberService memberService;
    private final AuditService auditService;

    public CashService(CashTransferRepository transferRepository, CashReceiptRepository receiptRepository,
                       CashCountRepository countRepository, CashAdjustmentRepository cashAdjustmentRepository,
                       MonthService monthService, MemberService memberService, AuditService auditService) {
        this.transferRepository = transferRepository;
        this.receiptRepository = receiptRepository;
        this.countRepository = countRepository;
        this.cashAdjustmentRepository = cashAdjustmentRepository;
        this.monthService = monthService;
        this.memberService = memberService;
        this.auditService = auditService;
    }

    // --------------------------------------------------------------- transfers

    @Transactional(readOnly = true)
    public List<CashTransfer> listTransfers(int year, int month) {
        return transferRepository.findByMonthIdOrderByTransferredOnAscIdAsc(monthService.find(year, month).getId());
    }

    @Transactional
    public CashTransfer createTransfer(int year, int month, TransferRequest request, CurrentUser actor) {
        FlatMonth flatMonth = monthService.findOpen(year, month);
        if (request.fromMemberId().equals(request.toMemberId())) {
            throw new IllegalArgumentException("A transfer needs two different people");
        }
        flatMonth.requireContains(request.transferredOn(), "Transfer date");
        memberService.get(request.fromMemberId());
        memberService.get(request.toMemberId());
        BigDecimal amount = MoneyUtil.requirePositiveMoney(request.amount(), "amount");

        CashTransfer transfer = transferRepository.save(new CashTransfer(flatMonth.getId(), actor.userId(),
                request.fromMemberId(), request.toMemberId(), amount, request.transferredOn(), blankToNull(request.note())));
        auditService.record(actor.userId(), "CASH_TRANSFER", transfer.getId(), AuditAction.CREATE,
                null, TransferResponse.from(transfer), null);
        return transfer;
    }

    @Transactional
    public CashTransfer voidTransfer(int year, int month, Long id, String reason, CurrentUser actor) {
        FlatMonth flatMonth = monthService.findOpen(year, month);
        CashTransfer transfer = transferRepository.findByIdAndMonthId(id, flatMonth.getId())
                .orElseThrow(() -> new NotFoundException("Transfer " + id + " not found in " + flatMonth.label()));
        TransferResponse before = TransferResponse.from(transfer);
        transfer.voidRecord(reason);
        auditService.record(actor.userId(), "CASH_TRANSFER", id, AuditAction.VOID,
                before, TransferResponse.from(transfer), reason);
        return transfer;
    }

    // ---------------------------------------------------------------- receipts

    @Transactional(readOnly = true)
    public List<CashReceipt> listReceipts(int year, int month) {
        return receiptRepository.findByMonthIdOrderByReceivedOnAscIdAsc(monthService.find(year, month).getId());
    }

    @Transactional
    public CashReceipt createReceipt(int year, int month, ReceiptRequest request, CurrentUser actor) {
        FlatMonth flatMonth = monthService.findOpen(year, month);
        flatMonth.requireContains(request.receivedOn(), "Received date");
        memberService.get(request.holderMemberId());
        BigDecimal amount = MoneyUtil.requirePositiveMoney(request.amount(), "amount");

        CashReceipt receipt = receiptRepository.save(new CashReceipt(flatMonth.getId(), actor.userId(),
                request.holderMemberId(), amount, request.source().trim(), request.receivedOn()));
        auditService.record(actor.userId(), "CASH_RECEIPT", receipt.getId(), AuditAction.CREATE,
                null, ReceiptResponse.from(receipt), null);
        return receipt;
    }

    @Transactional
    public CashReceipt voidReceipt(int year, int month, Long id, String reason, CurrentUser actor) {
        FlatMonth flatMonth = monthService.findOpen(year, month);
        CashReceipt receipt = receiptRepository.findByIdAndMonthId(id, flatMonth.getId())
                .orElseThrow(() -> new NotFoundException("Receipt " + id + " not found in " + flatMonth.label()));
        ReceiptResponse before = ReceiptResponse.from(receipt);
        receipt.voidRecord(reason);
        auditService.record(actor.userId(), "CASH_RECEIPT", id, AuditAction.VOID,
                before, ReceiptResponse.from(receipt), reason);
        return receipt;
    }

    // ------------------------------------------------------------------ counts

    @Transactional(readOnly = true)
    public List<CashCount> listCounts(int year, int month) {
        return countRepository.findByMonthIdOrderByIdAsc(monthService.find(year, month).getId());
    }

    @Transactional
    public CashCount createCount(int year, int month, CountRequest request, CurrentUser actor) {
        FlatMonth flatMonth = monthService.findOpen(year, month);
        memberService.get(request.holderMemberId());
        BigDecimal counted = MoneyUtil.requireNonNegativeMoney(request.countedAmount(), "countedAmount");

        CashCount count = countRepository.save(new CashCount(flatMonth.getId(), request.holderMemberId(),
                counted, blankToNull(request.note()), actor.userId()));
        auditService.record(actor.userId(), "CASH_COUNT", count.getId(), AuditAction.CREATE,
                null, CountResponse.from(count), null);
        return count;
    }

    // --------------------------------------------------------- cash adjustments

    @Transactional(readOnly = true)
    public List<CashAdjustment> listCashAdjustments(int year, int month) {
        return cashAdjustmentRepository.findByMonthIdOrderByIdAsc(monthService.find(year, month).getId());
    }

    @Transactional
    public CashAdjustment createCashAdjustment(int year, int month, CashAdjustmentRequest request, CurrentUser actor) {
        FlatMonth flatMonth = monthService.findOpen(year, month);
        memberService.get(request.holderMemberId());
        BigDecimal amount = MoneyUtil.requireMoney(request.amount(), "amount");
        if (amount.signum() == 0) {
            throw new IllegalArgumentException("A cash adjustment cannot be 0");
        }

        CashAdjustment adjustment = cashAdjustmentRepository.save(new CashAdjustment(flatMonth.getId(),
                actor.userId(), request.holderMemberId(), amount, request.type(), request.reason().trim()));
        auditService.record(actor.userId(), "CASH_ADJUSTMENT", adjustment.getId(), AuditAction.CREATE,
                null, CashAdjustmentResponse.from(adjustment), request.reason());
        return adjustment;
    }

    @Transactional
    public CashAdjustment voidCashAdjustment(int year, int month, Long id, String reason, CurrentUser actor) {
        FlatMonth flatMonth = monthService.findOpen(year, month);
        CashAdjustment adjustment = cashAdjustmentRepository.findByIdAndMonthId(id, flatMonth.getId())
                .orElseThrow(() -> new NotFoundException("Cash adjustment " + id + " not found in " + flatMonth.label()));
        CashAdjustmentResponse before = CashAdjustmentResponse.from(adjustment);
        adjustment.voidRecord(reason);
        auditService.record(actor.userId(), "CASH_ADJUSTMENT", id, AuditAction.VOID,
                before, CashAdjustmentResponse.from(adjustment), reason);
        return adjustment;
    }

    private static String blankToNull(String text) {
        return (text == null || text.isBlank()) ? null : text.trim();
    }
}
