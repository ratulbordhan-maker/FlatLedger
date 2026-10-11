package com.flatmeals.cash;

/**
 *   OPENING   cash a holder already had when you started using the app
 *   SHORTAGE  cash found missing after a count (use a negative amount)
 *   EXCESS    extra cash found after a count (positive amount)
 *   OTHER     any other approved correction
 */
public enum CashAdjustmentType {
    OPENING,
    SHORTAGE,
    EXCESS,
    OTHER
}
