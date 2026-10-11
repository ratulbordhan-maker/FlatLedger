package com.flatmeals.calc;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CostAllocatorTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    void splitsHundredThreeWaysWithoutLosingAPoisha() {
        Map<Long, BigDecimal> weights = new LinkedHashMap<>();
        weights.put(1L, bd("1"));
        weights.put(2L, bd("1"));
        weights.put(3L, bd("1"));

        Map<Long, BigDecimal> shares = CostAllocator.allocate(bd("100.00"), weights);

        // Plain rounding would give 33.33 × 3 = 99.99. The tie goes to the lowest id.
        assertThat(shares.get(1L)).isEqualByComparingTo("33.34");
        assertThat(shares.get(2L)).isEqualByComparingTo("33.33");
        assertThat(shares.get(3L)).isEqualByComparingTo("33.33");
        assertThat(MoneyUtil.sum(shares.values())).isEqualByComparingTo("100.00");
    }

    @Test
    void leftoverPoishaGoesToTheLargestRemainder() {
        Map<Long, BigDecimal> weights = new LinkedHashMap<>();
        weights.put(1L, bd("60"));
        weights.put(2L, bd("45.5"));
        weights.put(3L, bd("30.25"));

        Map<Long, BigDecimal> shares = CostAllocator.allocate(bd("12500.00"), weights);

        // Exact shares: 5524.86188…, 4189.68692…, 2785.45120…
        // Rounded down they total 12499.99; the extra poisha goes to member 2 (.692 is largest).
        assertThat(shares.get(1L)).isEqualByComparingTo("5524.86");
        assertThat(shares.get(2L)).isEqualByComparingTo("4189.69");
        assertThat(shares.get(3L)).isEqualByComparingTo("2785.45");
    }

    @Test
    void memberWithZeroWeightPaysNothing() {
        Map<Long, BigDecimal> weights = new LinkedHashMap<>();
        weights.put(1L, bd("1"));
        weights.put(2L, bd("0"));
        weights.put(3L, bd("2"));

        Map<Long, BigDecimal> shares = CostAllocator.allocate(bd("10.00"), weights);

        assertThat(shares.get(2L)).isEqualByComparingTo("0.00");
        assertThat(MoneyUtil.sum(shares.values())).isEqualByComparingTo("10.00");
    }

    @Test
    void zeroTotalGivesZeroToEveryone() {
        Map<Long, BigDecimal> weights = Map.of(1L, bd("5"), 2L, bd("3"));

        Map<Long, BigDecimal> shares = CostAllocator.allocate(bd("0.00"), weights);

        assertThat(shares.values()).allSatisfy(share -> assertThat(share).isEqualByComparingTo("0.00"));
    }

    @Test
    void sharesAlwaysAddUpExactly_randomisedCheck() {
        Random random = new Random(42); // fixed seed: the test is repeatable
        for (int run = 0; run < 2000; run++) {
            int members = 1 + random.nextInt(8);
            Map<Long, BigDecimal> weights = new LinkedHashMap<>();
            BigDecimal totalWeight = BigDecimal.ZERO;
            for (long id = 1; id <= members; id++) {
                BigDecimal meals = BigDecimal.valueOf(random.nextInt(249)).multiply(bd("0.25")); // 0..62 meals
                weights.put(id, meals);
                totalWeight = totalWeight.add(meals);
            }
            if (totalWeight.signum() == 0) {
                continue;
            }
            BigDecimal total = BigDecimal.valueOf(random.nextInt(5_000_000), 2); // up to ৳49,999.99

            Map<Long, BigDecimal> shares = CostAllocator.allocate(total, weights);

            assertThat(MoneyUtil.sum(shares.values())).isEqualByComparingTo(total);
            for (Map.Entry<Long, BigDecimal> entry : weights.entrySet()) {
                BigDecimal exact = total.multiply(entry.getValue()).divide(totalWeight, 10, RoundingMode.HALF_UP);
                BigDecimal error = shares.get(entry.getKey()).subtract(exact).abs();
                assertThat(error).isLessThan(bd("0.01")); // never more than one poisha off
            }
        }
    }

    @Test
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> CostAllocator.allocate(bd("10.00"), Map.of(1L, bd("0"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CostAllocator.allocate(bd("10.00"), Map.of(1L, bd("-1"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CostAllocator.allocate(bd("-5.00"), Map.of(1L, bd("1"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CostAllocator.allocate(bd("10.005"), Map.of(1L, bd("1"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
