package com.flatmeals.expense;

import com.flatmeals.common.RecordStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ExpenseDtos {

    private ExpenseDtos() {
    }

    /**
     * Create or edit an expense.
     *   inMealRate     optional, default true
     *   allowDuplicate optional: send true to save an expense that looks like a duplicate
     */
    public record ExpenseRequest(
            @NotNull LocalDate expenseDate,
            @NotBlank @Size(max = 50) String category,
            @Size(max = 255) String description,
            @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount,
            Boolean inMealRate,
            @NotNull FundingSource fundedBy,
            @NotNull Long holderMemberId,
            Boolean allowDuplicate) {
    }

    public record ExpenseResponse(
            Long id,
            LocalDate expenseDate,
            String category,
            String description,
            BigDecimal amount,
            boolean inMealRate,
            FundingSource fundedBy,
            Long holderMemberId,
            RecordStatus status,
            String voidReason) {

        public static ExpenseResponse from(Expense e) {
            return new ExpenseResponse(e.getId(), e.getExpenseDate(), e.getCategory(), e.getDescription(),
                    e.getAmount(), e.isInMealRate(), e.getFundedBy(), e.getHolderMemberId(),
                    e.getStatus(), e.getVoidReason());
        }
    }
}
