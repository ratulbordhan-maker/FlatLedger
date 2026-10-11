package com.flatmeals.calc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Calculates the monthly meal rate and each member's meal cost.
 *
 *   total meals     = Σ every member's meals
 *   meal rate       = total expenditure / total meals     (one rate for everyone)
 *   individual cost = total expenditure × member meals / total meals
 *
 * Note: individual costs are NOT calculated as "rounded rate × meals".
 * Using a rounded rate makes the costs drift away from the real total.
 * Instead, the total is split directly with CostAllocator, which guarantees
 * Σ costs = total expenditure, to the poisha.
 */
public final class MealRateCalculator {

    private MealRateCalculator() {
    }

    /**
     * @param mealsByMember member id → that member's total meals for the month
     *                      (include members with 0 meals so they appear with cost 0.00)
     * @param totalExpense  sum of the month's expenses that count toward the meal rate
     */
    public static MealRateResult calculate(Map<Long, BigDecimal> mealsByMember, BigDecimal totalExpense) {
        BigDecimal expense = MoneyUtil.requireNonNegativeMoney(totalExpense, "totalExpense");

        BigDecimal totalMeals = BigDecimal.ZERO.setScale(2);
        for (Map.Entry<Long, BigDecimal> entry : mealsByMember.entrySet()) {
            BigDecimal meals = entry.getValue();
            if (meals == null || meals.signum() < 0) {
                throw new IllegalArgumentException("Meals for member " + entry.getKey() + " must be ≥ 0");
            }
            totalMeals = totalMeals.add(meals);
        }

        // No meals: never divide by zero. Everyone's cost is 0.00 and the
        // result reports that the month cannot be finalized.
        if (totalMeals.signum() == 0) {
            Map<Long, BigDecimal> zeroCosts = new LinkedHashMap<>();
            for (Long memberId : mealsByMember.keySet()) {
                zeroCosts.put(memberId, MoneyUtil.zero());
            }
            return new MealRateResult(totalMeals, expense, null, zeroCosts);
        }

        BigDecimal mealRate = expense.divide(totalMeals, MoneyUtil.RATE_SCALE, MoneyUtil.ROUNDING);
        Map<Long, BigDecimal> costs = CostAllocator.allocate(expense, mealsByMember);
        return new MealRateResult(totalMeals, expense, mealRate, costs);
    }
}
