package com.flatmeals.calc;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MealRateCalculatorTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static Map<Long, BigDecimal> exampleMeals() {
        Map<Long, BigDecimal> meals = new LinkedHashMap<>();
        meals.put(1L, bd("60"));
        meals.put(2L, bd("45.5"));
        meals.put(3L, bd("30.25"));
        return meals;
    }

    @Test
    void calculatesOneSharedMealRate() {
        MealRateResult result = MealRateCalculator.calculate(exampleMeals(), bd("12500.00"));

        assertThat(result.totalMeals()).isEqualByComparingTo("135.75");
        assertThat(result.totalExpense()).isEqualByComparingTo("12500.00");
        assertThat(result.mealRate()).isEqualByComparingTo("92.081031"); // 12500 / 135.75
        assertThat(result.canBeFinalized()).isTrue();
        assertThat(result.warning()).isNull();
    }

    @Test
    void individualCostsAddUpToTotalExpenditure() {
        MealRateResult result = MealRateCalculator.calculate(exampleMeals(), bd("12500.00"));

        assertThat(result.memberCosts().get(1L)).isEqualByComparingTo("5524.86");
        assertThat(result.memberCosts().get(2L)).isEqualByComparingTo("4189.69");
        assertThat(result.memberCosts().get(3L)).isEqualByComparingTo("2785.45");
        assertThat(MoneyUtil.sum(result.memberCosts().values())).isEqualByComparingTo("12500.00");
    }

    @Test
    void zeroMealsDoesNotDivideByZeroAndCannotBeFinalized() {
        Map<Long, BigDecimal> meals = Map.of(1L, bd("0"), 2L, bd("0"));

        MealRateResult result = MealRateCalculator.calculate(meals, bd("500.00"));

        assertThat(result.hasNoMeals()).isTrue();
        assertThat(result.mealRate()).isNull();
        assertThat(result.canBeFinalized()).isFalse();
        assertThat(result.warning()).isNotBlank();
        assertThat(result.memberCosts().values())
                .allSatisfy(cost -> assertThat(cost).isEqualByComparingTo("0.00"));
    }

    @Test
    void noMembersAtAllIsTreatedAsZeroMeals() {
        MealRateResult result = MealRateCalculator.calculate(Map.of(), bd("0.00"));

        assertThat(result.canBeFinalized()).isFalse();
        assertThat(result.memberCosts()).isEmpty();
    }

    @Test
    void memberWhoDidNotEatHasZeroCost() {
        Map<Long, BigDecimal> meals = new LinkedHashMap<>(exampleMeals());
        meals.put(4L, bd("0"));

        MealRateResult result = MealRateCalculator.calculate(meals, bd("12500.00"));

        assertThat(result.memberCosts().get(4L)).isEqualByComparingTo("0.00");
        assertThat(MoneyUtil.sum(result.memberCosts().values())).isEqualByComparingTo("12500.00");
    }

    @Test
    void rejectsNegativeMealsAndBadExpense() {
        assertThatThrownBy(() -> MealRateCalculator.calculate(Map.of(1L, bd("-1")), bd("100.00")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MealRateCalculator.calculate(exampleMeals(), bd("-1.00")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MealRateCalculator.calculate(exampleMeals(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
