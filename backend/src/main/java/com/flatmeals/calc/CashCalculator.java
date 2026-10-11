package com.flatmeals.calc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Works out how much flat cash each holder should have, and compares it with
 * what was actually counted.
 *
 * Personal balances (what members owe) are handled by BalanceCalculator.
 * This class only answers: "where is the flat's physical money?"
 */
public final class CashCalculator {

    private CashCalculator() {
    }

    /**
     * Closing cash per holder = opening cash + money received − money paid out.
     *
     * @param openingCash holder id → cash at the start of the month
     * @param movements   all of the month's cash movements
     * @return holder id → recorded closing cash, sorted by holder id
     */
    public static Map<Long, BigDecimal> closingCash(Map<Long, BigDecimal> openingCash,
                                                    List<CashMovement> movements) {
        Map<Long, BigDecimal> cash = new TreeMap<>();
        for (Map.Entry<Long, BigDecimal> entry : openingCash.entrySet()) {
            cash.put(entry.getKey(), MoneyUtil.requireMoney(entry.getValue(), "opening cash"));
        }

        for (CashMovement movement : movements) {
            if (movement.fromHolderId() != null) {
                cash.merge(movement.fromHolderId(), movement.amount().negate(), BigDecimal::add);
            }
            if (movement.toHolderId() != null) {
                cash.merge(movement.toHolderId(), movement.amount(), BigDecimal::add);
            }
        }

        cash.replaceAll((holder, amount) -> amount.setScale(MoneyUtil.MONEY_SCALE));
        return cash;
    }

    /** Total flat cash across all holders. */
    public static BigDecimal totalCash(Map<Long, BigDecimal> cashByHolder) {
        return MoneyUtil.sum(cashByHolder.values());
    }

    /**
     * Compares recorded cash with physically counted cash, holder by holder.
     *
     * @param recorded holder id → recorded cash (from closingCash)
     * @param counted  holder id → counted cash; holders missing here are NOT_COUNTED
     */
    public static List<ReconciliationLine> reconcile(Map<Long, BigDecimal> recorded,
                                                     Map<Long, BigDecimal> counted) {
        // Include every holder that appears in either map
        Map<Long, BigDecimal> allRecorded = new TreeMap<>(recorded);
        for (Long holderId : counted.keySet()) {
            allRecorded.putIfAbsent(holderId, MoneyUtil.zero());
        }

        List<ReconciliationLine> lines = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : allRecorded.entrySet()) {
            Long holderId = entry.getKey();
            BigDecimal recordedAmount = entry.getValue().setScale(MoneyUtil.MONEY_SCALE);
            BigDecimal countedAmount = counted.get(holderId);

            if (countedAmount == null) {
                lines.add(new ReconciliationLine(holderId, recordedAmount, null, null,
                        ReconciliationLine.Status.NOT_COUNTED));
                continue;
            }

            countedAmount = MoneyUtil.requireNonNegativeMoney(countedAmount, "counted cash");
            BigDecimal difference = countedAmount.subtract(recordedAmount);
            ReconciliationLine.Status status = switch (difference.signum()) {
                case 0 -> ReconciliationLine.Status.MATCHED;
                case -1 -> ReconciliationLine.Status.SHORT;
                default -> ReconciliationLine.Status.OVER;
            };
            lines.add(new ReconciliationLine(holderId, recordedAmount, countedAmount, difference, status));
        }
        return lines;
    }
}
