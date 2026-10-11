package com.flatmeals.payment;

import com.flatmeals.common.RecordStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    /**
     *   type           optional, default PAYMENT
     *   cashHolderId   who received the cash (PAYMENT) or handed it back (REFUND)
     *   allowDuplicate optional: send true to save a payment that looks like a duplicate
     */
    public record PaymentRequest(
            @NotNull Long memberId,
            @NotNull Long cashHolderId,
            @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount,
            PaymentType type,
            @NotNull LocalDate paidOn,
            @Size(max = 255) String note,
            Boolean allowDuplicate) {
    }

    public record PaymentResponse(
            Long id,
            Long memberId,
            Long cashHolderId,
            BigDecimal amount,
            PaymentType type,
            LocalDate paidOn,
            String note,
            RecordStatus status,
            String voidReason) {

        public static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getMemberId(), p.getCashHolderId(), p.getAmount(),
                    p.getType(), p.getPaidOn(), p.getNote(), p.getStatus(), p.getVoidReason());
        }
    }

    /** amount is signed: positive = member owes more, negative = credit. Must not be 0. */
    public record AdjustmentRequest(
            @NotNull Long memberId,
            @NotNull @Digits(integer = 10, fraction = 2) BigDecimal amount,
            AdjustmentType type,
            @NotBlank @Size(max = 255) String reason) {
    }

    public record AdjustmentResponse(
            Long id,
            Long memberId,
            BigDecimal amount,
            AdjustmentType type,
            String reason,
            RecordStatus status,
            String voidReason) {

        public static AdjustmentResponse from(Adjustment a) {
            return new AdjustmentResponse(a.getId(), a.getMemberId(), a.getAmount(), a.getType(),
                    a.getReason(), a.getStatus(), a.getVoidReason());
        }
    }
}
