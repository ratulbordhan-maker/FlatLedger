package com.flatmeals.calc;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The full worked example from the design, run end to end:
 * meals → meal rate → costs → balances → cash → reconciliation → fund check.
 *
 * Members: A = 1 (manager, holds cash), B = 2 (also holds some cash), C = 3.
 */
class WorkedExampleTest {

    private static final Long A = 1L;
    private static final Long B = 2L;
    private static final Long C = 3L;

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    void wholeMonthReconciles() {
        // ---- Meals and expenses -------------------------------------------
        Map<Long, BigDecimal> meals = new LinkedHashMap<>();
        meals.put(A, bd("60"));
        meals.put(B, bd("45.5"));
        meals.put(C, bd("30.25"));

        // Expenses: A 9000 (flat cash) + B 1500 (flat cash) + C 2000 (own pocket)
        BigDecimal totalExpense = bd("12500.00");

        MealRateResult rate = MealRateCalculator.calculate(meals, totalExpense);
        assertThat(rate.mealRate()).isEqualByComparingTo("92.081031");

        // ---- Personal balances ---------------------------------------------
        Map<Long, MemberMonthInput> accounts = new LinkedHashMap<>();
        accounts.put(A, new MemberMonthInput(bd("300.00"), rate.memberCosts().get(A),
                bd("5000.00"), bd("0"), bd("0"), bd("0")));
        accounts.put(B, new MemberMonthInput(bd("-200.00"), rate.memberCosts().get(B),
                bd("4500.00"), bd("0"), bd("0"), bd("0")));
        accounts.put(C, new MemberMonthInput(bd("0.00"), rate.memberCosts().get(C),
                bd("2000.00"), bd("0"), bd("0"), bd("2000.00")));

        Map<Long, BigDecimal> closingBalances = new LinkedHashMap<>();
        accounts.forEach((id, input) -> closingBalances.put(id, BalanceCalculator.closingBalance(input)));

        assertThat(closingBalances.get(A)).isEqualByComparingTo("824.86");    // owes
        assertThat(closingBalances.get(B)).isEqualByComparingTo("-510.31");   // credit
        assertThat(closingBalances.get(C)).isEqualByComparingTo("-1214.55");  // credit

        // ---- Cash -----------------------------------------------------------
        Map<Long, BigDecimal> openingCash = Map.of(A, bd("1000.00"), B, bd("0.00"));
        List<CashMovement> movements = List.of(
                CashMovement.paymentReceived(A, bd("5000.00")),   // A pays, A holds it
                CashMovement.paymentReceived(A, bd("4500.00")),   // B pays A
                CashMovement.paymentReceived(B, bd("2000.00")),   // C pays B
                CashMovement.expensePaid(A, bd("9000.00")),       // A buys groceries
                CashMovement.expensePaid(B, bd("1500.00")),       // B buys groceries
                // C's 2000 out-of-pocket groceries never touched flat cash: no movement
                CashMovement.transfer(B, A, bd("300.00")));       // B hands 300 to A

        Map<Long, BigDecimal> closingCash = CashCalculator.closingCash(openingCash, movements);
        assertThat(closingCash.get(A)).isEqualByComparingTo("1800.00");
        assertThat(closingCash.get(B)).isEqualByComparingTo("200.00");
        assertThat(CashCalculator.totalCash(closingCash)).isEqualByComparingTo("2000.00");

        // B holds 200 of flat cash, but her personal credit is still exactly -510.31
        assertThat(closingBalances.get(B)).isEqualByComparingTo("-510.31");

        // ---- Reconciliation: A counts 1750, B counts 200 --------------------
        List<ReconciliationLine> report = CashCalculator.reconcile(closingCash,
                Map.of(A, bd("1750.00"), B, bd("200.00")));
        assertThat(report.get(0).status()).isEqualTo(ReconciliationLine.Status.SHORT);
        assertThat(report.get(0).difference()).isEqualByComparingTo("-50.00");
        assertThat(report.get(1).status()).isEqualTo(ReconciliationLine.Status.MATCHED);

        // ---- Fund identity --------------------------------------------------
        // flat cash + Σ member balances must not change during a normal meal month.
        BigDecimal openingPosition = CashCalculator.totalCash(openingCash)
                .add(bd("300.00")).add(bd("-200.00")).add(bd("0.00"));
        BigDecimal closingPosition = CashCalculator.totalCash(closingCash)
                .add(MoneyUtil.sum(closingBalances.values()));
        assertThat(openingPosition).isEqualByComparingTo("1100.00");
        assertThat(closingPosition).isEqualByComparingTo(openingPosition);
    }
}
