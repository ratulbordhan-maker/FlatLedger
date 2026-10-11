package com.flatmeals.calc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MealValuesTest {

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.25", "0.5", "1", "1.5", "2", "2.00"})
    void acceptsSupportedMealValues(String value) {
        assertThat(MealValues.isValid(new BigDecimal(value))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.25", "0.3", "0.1", "2.25", "3"})
    void rejectsUnsupportedMealValues(String value) {
        assertThat(MealValues.isValid(new BigDecimal(value))).isFalse();
    }

    @Test
    void rejectsNull() {
        assertThat(MealValues.isValid(null)).isFalse();
        assertThatThrownBy(() -> MealValues.requireValid(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void knowsTheRealNumberOfDaysInEachMonth() {
        assertThat(MealValues.daysInMonth(2026, 1)).isEqualTo(31);
        assertThat(MealValues.daysInMonth(2026, 4)).isEqualTo(30);
        assertThat(MealValues.daysInMonth(2026, 2)).isEqualTo(28);   // normal year
        assertThat(MealValues.daysInMonth(2028, 2)).isEqualTo(29);   // leap year
        assertThat(MealValues.daysInMonth(2100, 2)).isEqualTo(28);   // century, not a leap year
        assertThat(MealValues.daysInMonth(2000, 2)).isEqualTo(29);   // divisible by 400, leap year
    }
}
