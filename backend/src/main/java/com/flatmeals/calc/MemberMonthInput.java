package com.flatmeals.calc;

import java.math.BigDecimal;

/**
 * Everything that affects one member's account in one month.
 *
 * Sign convention for balances: POSITIVE = member owes the flat,
 * NEGATIVE = member has credit (the flat owes them).
 *
 * @param openingBalance last month's closing balance (signed)
 * @param mealCost       this month's meal cost (≥ 0)
 * @param payments       money the member paid in to the flat (≥ 0)
 * @param refunds        money the flat paid back to the member (≥ 0)
 * @param adjustments    authorised corrections (signed: + means the member owes more)
 * @param pocketCredit   expenses the member paid from their own pocket (≥ 0)
 */
public record MemberMonthInput(
        BigDecimal openingBalance,
        BigDecimal mealCost,
        BigDecimal payments,
        BigDecimal refunds,
        BigDecimal adjustments,
        BigDecimal pocketCredit) {

    /** The compact constructor runs before the fields are set: we validate here. */
    public MemberMonthInput {
        openingBalance = MoneyUtil.requireMoney(openingBalance, "openingBalance");
        mealCost = MoneyUtil.requireNonNegativeMoney(mealCost, "mealCost");
        payments = MoneyUtil.requireNonNegativeMoney(payments, "payments");
        refunds = MoneyUtil.requireNonNegativeMoney(refunds, "refunds");
        adjustments = MoneyUtil.requireMoney(adjustments, "adjustments");
        pocketCredit = MoneyUtil.requireNonNegativeMoney(pocketCredit, "pocketCredit");
    }
}
