package com.flatmeals.calc;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CashCalculatorTest {

    private static final Long MANAGER = 1L;
    private static final Long HELPER = 2L;

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    void paymentsIncreaseAndExpensesDecreaseHolderCash() {
        Map<Long, BigDecimal> cash = CashCalculator.closingCash(
                Map.of(MANAGER, bd("1000.00")),
                List.of(CashMovement.paymentReceived(MANAGER, bd("5000.00")),
                        CashMovement.expensePaid(MANAGER, bd("4200.00"))));

        assertThat(cash.get(MANAGER)).isEqualByComparingTo("1800.00");
    }

    @Test
    void transferMovesCashButDoesNotChangeTheFlatTotal() {
        Map<Long, BigDecimal> opening = Map.of(MANAGER, bd("1000.00"), HELPER, bd("500.00"));

        Map<Long, BigDecimal> cash = CashCalculator.closingCash(opening,
                List.of(CashMovement.transfer(HELPER, MANAGER, bd("300.00"))));

        assertThat(cash.get(MANAGER)).isEqualByComparingTo("1300.00");
        assertThat(cash.get(HELPER)).isEqualByComparingTo("200.00");
        assertThat(CashCalculator.totalCash(cash)).isEqualByComparingTo("1500.00"); // unchanged
    }

    @Test
    void holderWithNoOpeningCashStartsAtZero() {
        Map<Long, BigDecimal> cash = CashCalculator.closingCash(Map.of(),
                List.of(CashMovement.paymentReceived(HELPER, bd("2000.00"))));

        assertThat(cash.get(HELPER)).isEqualByComparingTo("2000.00");
    }

    @Test
    void otherReceiptsRefundsAndAdjustmentsMoveCash() {
        Map<Long, BigDecimal> cash = CashCalculator.closingCash(Map.of(MANAGER, bd("1000.00")),
                List.of(CashMovement.otherReceipt(MANAGER, bd("150.00")),
                        CashMovement.refundPaid(MANAGER, bd("100.00")),
                        CashMovement.adjustment(MANAGER, bd("-50.00")),
                        CashMovement.adjustment(MANAGER, bd("20.00"))));

        assertThat(cash.get(MANAGER)).isEqualByComparingTo("1020.00");
    }

    @Test
    void reconciliationReportsMatchShortOverAndNotCounted() {
        Map<Long, BigDecimal> recorded = Map.of(1L, bd("1800.00"), 2L, bd("200.00"),
                3L, bd("500.00"), 4L, bd("100.00"));
        Map<Long, BigDecimal> counted = Map.of(1L, bd("1750.00"), 2L, bd("200.00"), 3L, bd("520.00"));

        List<ReconciliationLine> lines = CashCalculator.reconcile(recorded, counted);

        assertThat(lines).hasSize(4);
        assertThat(lines.get(0).status()).isEqualTo(ReconciliationLine.Status.SHORT);
        assertThat(lines.get(0).difference()).isEqualByComparingTo("-50.00");
        assertThat(lines.get(1).status()).isEqualTo(ReconciliationLine.Status.MATCHED);
        assertThat(lines.get(1).difference()).isEqualByComparingTo("0.00");
        assertThat(lines.get(2).status()).isEqualTo(ReconciliationLine.Status.OVER);
        assertThat(lines.get(2).difference()).isEqualByComparingTo("20.00");
        assertThat(lines.get(3).status()).isEqualTo(ReconciliationLine.Status.NOT_COUNTED);
        assertThat(lines.get(3).difference()).isNull();
    }

    @Test
    void countedHolderWithNoRecordsIsFlaggedAsOver() {
        List<ReconciliationLine> lines = CashCalculator.reconcile(Map.of(), Map.of(5L, bd("40.00")));

        assertThat(lines.get(0).recorded()).isEqualByComparingTo("0.00");
        assertThat(lines.get(0).status()).isEqualTo(ReconciliationLine.Status.OVER);
    }

    @Test
    void rejectsInvalidMovements() {
        assertThatThrownBy(() -> CashMovement.paymentReceived(MANAGER, bd("0")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CashMovement.expensePaid(MANAGER, bd("-10.00")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CashMovement.transfer(MANAGER, MANAGER, bd("10.00")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CashMovement.adjustment(MANAGER, bd("0")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CashMovement(null, null, bd("10.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
