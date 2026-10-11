package com.flatmeals.calc;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Result of a month's meal-rate calculation.
 *
 * A "record" is a short way to write a class that just holds values:
 * Java creates the constructor, getters (totalMeals(), mealRate(), ...),
 * equals, hashCode and toString for us.
 *
 * @param totalMeals   sum of all members' meals in the month
 * @param totalExpense sum of expenses included in the meal rate
 * @param mealRate     totalExpense / totalMeals with 6 decimals, or null when there are no meals
 * @param memberCosts  member id → meal cost (2 decimals); always adds up to totalExpense
 *                     when there are meals, all 0.00 when there are none
 */
public record MealRateResult(
        BigDecimal totalMeals,
        BigDecimal totalExpense,
        BigDecimal mealRate,
        Map<Long, BigDecimal> memberCosts) {

    /** True when nobody ate this month: we cannot divide by zero. */
    public boolean hasNoMeals() {
        return totalMeals.signum() == 0;
    }

    /** A month can only be closed (finalized) if its meal rate exists. */
    public boolean canBeFinalized() {
        return !hasNoMeals();
    }

    /** Message for the dashboard, or null when everything is fine. */
    public String warning() {
        return hasNoMeals()
                ? "No meals recorded this month, so the meal rate cannot be calculated."
                : null;
    }
}
