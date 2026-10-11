package com.flatmeals.calc;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BalanceCalculatorTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    /** Small helper so tests read like a sentence. */
    private static BigDecimal closing(String opening, String mealCost, String payments,
                                      String refunds, String adjustments, String pocketCredit) {
        return BalanceCalculator.closingBalance(new MemberMonthInput(
                bd(opening), bd(mealCost), bd(payments), bd(refunds), bd(adjustments), bd(pocketCredit)));
    }

    @Test
    void previousDuePlusMealCostMinusPayment() {
        // Owed 300 from last month, ate 5524.86, paid 5000 → still owes 824.86
        assertThat(closing("300.00", "5524.86", "5000.00", "0", "0", "0")).isEqualByComparingTo("824.86");
    }

    @Test
    void previousCreditIsUsedUp() {
        // Had 200 credit, ate 4189.69, paid 4500 → 510.31 credit
        BigDecimal balance = closing("-200.00", "4189.69", "4500.00", "0", "0", "0");
        assertThat(balance).isEqualByComparingTo("-510.31");
        assertThat(BalanceCalculator.hasCredit(balance)).isTrue();
        assertThat(BalanceCalculator.owesFlat(balance)).isFalse();
    }

    @Test
    void outOfPocketExpenseIsCredited() {
        // Ate 2785.45, paid 2000, bought groceries for the flat with own money 2000
        assertThat(closing("0", "2785.45", "2000.00", "0", "0", "2000.00")).isEqualByComparingTo("-1214.55");
    }

    @Test
    void adjustmentsAreSigned() {
        assertThat(closing("0", "1000.00", "0", "0", "50.00", "0")).isEqualByComparingTo("1050.00");
        assertThat(closing("0", "1000.00", "0", "0", "-50.00", "0")).isEqualByComparingTo("950.00");
    }

    @Test
    void refundSettlesACredit() {
        // A leaving member with 510.31 credit is paid back in cash → balance 0
        assertThat(closing("-510.31", "0", "0", "510.31", "0", "0")).isEqualByComparingTo("0.00");
    }

    @Test
    void closingBalanceCarriesForwardWithoutDuplicatingTransactions() {
        // Month 1: ate 3000, paid 2500 → owes 500
        BigDecimal september = closing("0", "3000.00", "2500.00", "0", "0", "0");
        assertThat(september).isEqualByComparingTo("500.00");

        // Month 2 starts from month 1's closing. Only month 2's own transactions are added.
        BigDecimal october = closing(september.toPlainString(), "2800.00", "3300.00", "0", "0", "0");
        assertThat(october).isEqualByComparingTo("0.00");

        // Month 3: nothing happened → balance stays where it was
        BigDecimal november = closing(october.toPlainString(), "0", "0", "0", "0", "0");
        assertThat(november).isEqualByComparingTo("0.00");
    }

    @Test
    void rejectsNegativeAmountsWhereTheyMakeNoSense() {
        assertThatThrownBy(() -> closing("0", "-1.00", "0", "0", "0", "0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> closing("0", "0", "-1.00", "0", "0", "0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> closing("0", "0", "0", "0", "0", "-1.00"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> closing("0", "10.001", "0", "0", "0", "0"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
