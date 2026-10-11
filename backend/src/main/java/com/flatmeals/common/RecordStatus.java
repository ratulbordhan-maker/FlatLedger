package com.flatmeals.common;

/**
 * Status of a financial record (expense, payment, transfer, ...).
 * Records are never deleted: a mistake is VOIDED with a reason, and voided
 * records stop counting in every total but stay visible in the history.
 */
public enum RecordStatus {
    ACTIVE,
    VOIDED
}
