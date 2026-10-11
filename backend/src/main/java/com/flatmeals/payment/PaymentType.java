package com.flatmeals.payment;

/**
 *   PAYMENT  a member pays the flat: their balance goes DOWN, the holder's cash goes UP.
 *   REFUND   the flat pays a member back (usually their credit): their balance goes UP
 *            (towards zero), the holder's cash goes DOWN.
 */
public enum PaymentType {
    PAYMENT,
    REFUND
}
