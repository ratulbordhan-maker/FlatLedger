package com.flatmeals.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * Shared money rules for the whole app.
 *
 * Why BigDecimal and not double?  0.1 + 0.2 in double is 0.30000000000000004.
 * BigDecimal stores exact decimal numbers, so ৳12,500.00 stays exactly ৳12,500.00.
 */
public final class MoneyUtil {

    /** Taka are stored with 2 decimals (poisha). */
    public static final int MONEY_SCALE = 2;

    /** The meal rate is kept with 6 decimals (it is shown rounded to 2). */
    public static final int RATE_SCALE = 6;

    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private MoneyUtil() {
        // utility class: no objects
    }

    /** Zero with 2 decimals: 0.00 */
    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE);
    }

    /** Adds up a list of amounts. An empty list gives 0.00. */
    public static BigDecimal sum(Collection<BigDecimal> values) {
        BigDecimal total = zero();
        for (BigDecimal value : values) {
            total = total.add(value);
        }
        return total.setScale(MONEY_SCALE, ROUNDING);
    }

    /**
     * Checks that a value is a valid money amount: not null and at most 2 decimals.
     * We reject 10.005 instead of silently rounding it, so totals always add up exactly.
     */
    public static BigDecimal requireMoney(BigDecimal value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (value.stripTrailingZeros().scale() > MONEY_SCALE) {
            throw new IllegalArgumentException(fieldName + " can have at most 2 decimals: " + value);
        }
        return value.setScale(MONEY_SCALE);
    }

    /** Like requireMoney, but also rejects negative amounts. */
    public static BigDecimal requireNonNegativeMoney(BigDecimal value, String fieldName) {
        BigDecimal money = requireMoney(value, fieldName);
        if (money.signum() < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative: " + value);
        }
        return money;
    }

    /** Like requireMoney, but the amount must be greater than zero. */
    public static BigDecimal requirePositiveMoney(BigDecimal value, String fieldName) {
        BigDecimal money = requireMoney(value, fieldName);
        if (money.signum() <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero: " + value);
        }
        return money;
    }
}
