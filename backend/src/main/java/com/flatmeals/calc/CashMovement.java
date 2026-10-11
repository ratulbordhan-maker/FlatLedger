package com.flatmeals.calc;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One movement of the flat's physical cash.
 *
 * Every movement goes FROM someone TO someone:
 *   - a holder id means "a person holding cash for the flat"
 *   - null means "outside the flat's cash" (a member's own pocket, a shop, ...)
 *
 * This one idea prevents double counting:
 *   payment received   null   → holder   (flat cash goes up)
 *   expense paid       holder → null     (flat cash goes down)
 *   refund paid        holder → null     (flat cash goes down)
 *   other receipt      null   → holder   (flat cash goes up)
 *   transfer           holder → holder   (total flat cash does NOT change)
 *
 * The amount is always positive; direction is given by from/to.
 * Use the factory methods below instead of the constructor.
 */
public record CashMovement(Long fromHolderId, Long toHolderId, BigDecimal amount) {

    public CashMovement {
        amount = MoneyUtil.requirePositiveMoney(amount, "amount");
        if (fromHolderId == null && toHolderId == null) {
            throw new IllegalArgumentException("A cash movement needs a sender or a receiver");
        }
        if (Objects.equals(fromHolderId, toHolderId)) {
            throw new IllegalArgumentException("Cannot transfer cash to the same person");
        }
    }

    /** A member's payment, received by the holder who took the cash. */
    public static CashMovement paymentReceived(Long holderId, BigDecimal amount) {
        return new CashMovement(null, requireHolder(holderId), amount);
    }

    /** Money from outside the meal payments (e.g. sold an old gas cylinder). */
    public static CashMovement otherReceipt(Long holderId, BigDecimal amount) {
        return new CashMovement(null, requireHolder(holderId), amount);
    }

    /** An expense paid out of flat cash by the holder. (Not for out-of-pocket expenses.) */
    public static CashMovement expensePaid(Long holderId, BigDecimal amount) {
        return new CashMovement(requireHolder(holderId), null, amount);
    }

    /** The flat paying a member's credit back in cash. */
    public static CashMovement refundPaid(Long holderId, BigDecimal amount) {
        return new CashMovement(requireHolder(holderId), null, amount);
    }

    /** Cash handed from one holder to another. Neither income nor expense. */
    public static CashMovement transfer(Long fromHolderId, Long toHolderId, BigDecimal amount) {
        return new CashMovement(requireHolder(fromHolderId), requireHolder(toHolderId), amount);
    }

    /** An approved correction: positive adds cash to the holder, negative removes it. */
    public static CashMovement adjustment(Long holderId, BigDecimal signedAmount) {
        BigDecimal value = MoneyUtil.requireMoney(signedAmount, "adjustment");
        if (value.signum() == 0) {
            throw new IllegalArgumentException("Adjustment cannot be zero");
        }
        return value.signum() > 0
                ? new CashMovement(null, requireHolder(holderId), value)
                : new CashMovement(requireHolder(holderId), null, value.negate());
    }

    private static Long requireHolder(Long holderId) {
        if (holderId == null) {
            throw new IllegalArgumentException("Holder id is required");
        }
        return holderId;
    }
}
