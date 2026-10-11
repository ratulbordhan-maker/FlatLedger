package com.flatmeals.calc;

import java.math.BigDecimal;

/**
 * One row of the cash reconciliation report.
 *
 * @param holderId   the person holding flat cash
 * @param recorded   what the records say they should have
 * @param counted    what was physically counted, or null if not counted yet
 * @param difference counted − recorded (negative = cash is missing), or null if not counted
 * @param status     MATCHED, SHORT, OVER or NOT_COUNTED
 */
public record ReconciliationLine(
        Long holderId,
        BigDecimal recorded,
        BigDecimal counted,
        BigDecimal difference,
        Status status) {

    public enum Status { MATCHED, SHORT, OVER, NOT_COUNTED }
}
