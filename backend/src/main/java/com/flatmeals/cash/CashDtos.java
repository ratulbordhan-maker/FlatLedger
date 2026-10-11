package com.flatmeals.cash;

import com.flatmeals.common.RecordStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class CashDtos {

    private CashDtos() {
    }

    public record TransferRequest(
            @NotNull Long fromMemberId,
            @NotNull Long toMemberId,
            @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotNull LocalDate transferredOn,
            @Size(max = 255) String note) {
    }

    public record TransferResponse(Long id, Long fromMemberId, Long toMemberId, BigDecimal amount,
                                   LocalDate transferredOn, String note, RecordStatus status, String voidReason) {
        public static TransferResponse from(CashTransfer t) {
            return new TransferResponse(t.getId(), t.getFromMemberId(), t.getToMemberId(), t.getAmount(),
                    t.getTransferredOn(), t.getNote(), t.getStatus(), t.getVoidReason());
        }
    }

    public record ReceiptRequest(
            @NotNull Long holderMemberId,
            @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 255) String source,
            @NotNull LocalDate receivedOn) {
    }

    public record ReceiptResponse(Long id, Long holderMemberId, BigDecimal amount, String source,
                                  LocalDate receivedOn, RecordStatus status, String voidReason) {
        public static ReceiptResponse from(CashReceipt r) {
            return new ReceiptResponse(r.getId(), r.getHolderMemberId(), r.getAmount(), r.getSource(),
                    r.getReceivedOn(), r.getStatus(), r.getVoidReason());
        }
    }

    public record CountRequest(
            @NotNull Long holderMemberId,
            @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal countedAmount,
            @Size(max = 255) String note) {
    }

    public record CountResponse(Long id, Long holderMemberId, BigDecimal countedAmount, Instant countedAt, String note) {
        public static CountResponse from(CashCount c) {
            return new CountResponse(c.getId(), c.getHolderMemberId(), c.getCountedAmount(), c.getCountedAt(), c.getNote());
        }
    }

    /** amount is signed and must not be 0. */
    public record CashAdjustmentRequest(
            @NotNull Long holderMemberId,
            @NotNull @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotNull CashAdjustmentType type,
            @NotBlank @Size(max = 255) String reason) {
    }

    public record CashAdjustmentResponse(Long id, Long holderMemberId, BigDecimal amount, CashAdjustmentType type,
                                         String reason, RecordStatus status, String voidReason) {
        public static CashAdjustmentResponse from(CashAdjustment a) {
            return new CashAdjustmentResponse(a.getId(), a.getHolderMemberId(), a.getAmount(), a.getType(),
                    a.getReason(), a.getStatus(), a.getVoidReason());
        }
    }
}
