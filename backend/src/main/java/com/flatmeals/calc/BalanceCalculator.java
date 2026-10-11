package com.flatmeals.calc;

import java.math.BigDecimal;

/**
 * Calculates a member's closing balance for a month.
 *
 *   closing = opening + meal cost + adjustments + refunds − payments − pocket credit
 *
 *   + meal cost     the member ate, so they owe more
 *   + adjustments   signed corrections approved by the manager
 *   + refunds       the flat paid credit back, so that credit is used up
 *   − payments      the member paid the flat
 *   − pocket credit the member paid a shared expense from their own money
 *
 * Carry-forward: next month's opening balance is simply this closing balance.
 * Nothing is copied as a new transaction, so nothing can be counted twice.
 *
 * Cash the member HOLDS for the flat is deliberately NOT part of this formula:
 * holding the flat's money does not change what the member personally owes.
 */
public final class BalanceCalculator {

    private BalanceCalculator() {
    }

    public static BigDecimal closingBalance(MemberMonthInput input) {
        return input.openingBalance()
                .add(input.mealCost())
                .add(input.adjustments())
                .add(input.refunds())
                .subtract(input.payments())
                .subtract(input.pocketCredit())
                .setScale(MoneyUtil.MONEY_SCALE);
    }

    /** Readable helpers for the UI. */
    public static boolean owesFlat(BigDecimal balance) {
        return balance.signum() > 0;
    }

    public static boolean hasCredit(BigDecimal balance) {
        return balance.signum() < 0;
    }
}
