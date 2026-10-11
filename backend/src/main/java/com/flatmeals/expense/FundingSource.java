package com.flatmeals.expense;

/**
 * Where the money for an expense came from (expenses.funded_by).
 *
 *   FLAT_CASH      paid from the flat's cash held by that person:
 *                  the holder's cash goes down; nobody's personal balance changes.
 *   MEMBER_POCKET  the person paid with their OWN money:
 *                  flat cash does not change; the person's account is credited.
 */
public enum FundingSource {
    FLAT_CASH,
    MEMBER_POCKET
}
