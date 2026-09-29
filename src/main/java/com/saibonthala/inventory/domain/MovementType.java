package com.saibonthala.inventory.domain;

/** Every change to stock is recorded as one of these ledger entry types. */
public enum MovementType {
    RECEIPT,
    SALE,
    ADJUSTMENT,
    TRANSFER_OUT,
    TRANSFER_IN
}
