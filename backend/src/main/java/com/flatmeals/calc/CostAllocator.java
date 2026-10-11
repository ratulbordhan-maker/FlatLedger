package com.flatmeals.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Splits a total amount between members in proportion to their weights
 * (their meal counts), so that the shares ALWAYS add up exactly to the total.
 *
 * The rounding problem
 * --------------------
 * ৳100 split between 3 equal members is 33.333... each. Rounding each share
 * gives 33.33 + 33.33 + 33.33 = 99.99, and one poisha disappears.
 *
 * The fix: the largest-remainder method
 * -------------------------------------
 *  1. Work in poisha (cents) so we deal with whole numbers.
 *  2. Give every member their exact share rounded DOWN.
 *  3. Count the leftover poisha (always fewer than the number of members).
 *  4. Hand them out one at a time to the members whose share lost the most
 *     when rounding down. Ties go to the lowest member id, so the result is
 *     the same every time.
 *
 * A member with weight 0 never receives a leftover poisha.
 */
public final class CostAllocator {

    private CostAllocator() {
    }

    /**
     * @param total   the amount to split, e.g. total expenditure (≥ 0, max 2 decimals)
     * @param weights member id → weight (meal count); weights must be ≥ 0 and not all zero
     * @return member id → share, with 2 decimals, in the same order as the input
     */
    public static Map<Long, BigDecimal> allocate(BigDecimal total, Map<Long, BigDecimal> weights) {
        BigDecimal amount = MoneyUtil.requireNonNegativeMoney(total, "total");

        BigDecimal totalWeight = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> entry : weights.entrySet()) {
            if (entry.getValue() == null || entry.getValue().signum() < 0) {
                throw new IllegalArgumentException("Weight for member " + entry.getKey() + " must be ≥ 0");
            }
            totalWeight = totalWeight.add(entry.getValue());
        }
        if (totalWeight.signum() == 0) {
            throw new IllegalArgumentException("Cannot split an amount when all weights are zero");
        }

        // Step 1: total in poisha, e.g. 12500.00 → 1250000
        BigDecimal totalPoisha = amount.movePointRight(MoneyUtil.MONEY_SCALE);

        // Step 2: each member's share rounded down, plus what was cut off.
        // exactShare = totalPoisha × weight / totalWeight
        // We keep the cut-off part as "numerator remainder" so no precision is lost.
        List<Share> shares = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> entry : weights.entrySet()) {
            BigDecimal numerator = totalPoisha.multiply(entry.getValue());
            BigDecimal floorPoisha = numerator.divide(totalWeight, 0, RoundingMode.FLOOR);
            BigDecimal remainder = numerator.subtract(floorPoisha.multiply(totalWeight));
            shares.add(new Share(entry.getKey(), floorPoisha, remainder));
            allocated = allocated.add(floorPoisha);
        }

        // Step 3: leftover poisha after rounding everyone down
        int leftover = totalPoisha.subtract(allocated).intValueExact();

        // Step 4: biggest remainder first; ties → lowest member id
        List<Share> byRemainder = new ArrayList<>(shares);
        byRemainder.sort(Comparator.comparing(Share::remainder).reversed()
                .thenComparing(Share::memberId));
        for (int i = 0; i < leftover; i++) {
            byRemainder.get(i).addOnePoisha();
        }

        // Convert back from poisha to taka, keeping the original member order
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (Share share : shares) {
            result.put(share.memberId(),
                    share.poisha().movePointLeft(MoneyUtil.MONEY_SCALE).setScale(MoneyUtil.MONEY_SCALE));
        }
        return result;
    }

    /** Working data for one member during the calculation. */
    private static final class Share {
        private final Long memberId;
        private BigDecimal poisha;
        private final BigDecimal remainder;

        Share(Long memberId, BigDecimal poisha, BigDecimal remainder) {
            this.memberId = memberId;
            this.poisha = poisha;
            this.remainder = remainder;
        }

        Long memberId() {
            return memberId;
        }

        BigDecimal poisha() {
            return poisha;
        }

        BigDecimal remainder() {
            return remainder;
        }

        void addOnePoisha() {
            poisha = poisha.add(BigDecimal.ONE);
        }
    }
}
