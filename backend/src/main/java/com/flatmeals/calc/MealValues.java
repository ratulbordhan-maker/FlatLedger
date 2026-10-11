package com.flatmeals.calc;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * Rules for a single day's meal value, matching the database CHECK constraint
 * ck_meal_value: from 0 to 2 in steps of 0.25.
 *
 *   2.00 = lunch + dinner     1.00 = lunch only / dinner only
 *   0.50 = rice-only meal     0.25 = rice, half portion     0.00 = no meal
 *   (combinations such as 1.50 are allowed too)
 */
public final class MealValues {

    public static final BigDecimal MIN = BigDecimal.ZERO;
    public static final BigDecimal MAX = new BigDecimal("2");
    public static final BigDecimal STEP = new BigDecimal("0.25");

    private MealValues() {
    }

    /** True if the value is between 0 and 2 and a multiple of 0.25. */
    public static boolean isValid(BigDecimal value) {
        if (value == null) {
            return false;
        }
        boolean inRange = value.compareTo(MIN) >= 0 && value.compareTo(MAX) <= 0;
        boolean onStep = value.remainder(STEP).signum() == 0;
        return inRange && onStep;
    }

    /** Throws if the value is not a valid daily meal value. */
    public static BigDecimal requireValid(BigDecimal value) {
        if (!isValid(value)) {
            throw new IllegalArgumentException(
                    "Meal value must be between 0 and 2 in steps of 0.25, got: " + value);
        }
        return value.setScale(2);
    }

    /**
     * Number of days in a month. Java's YearMonth already knows about
     * leap years, so February 2028 gives 29 and February 2026 gives 28.
     */
    public static int daysInMonth(int year, int month) {
        return YearMonth.of(year, month).lengthOfMonth();
    }
}
