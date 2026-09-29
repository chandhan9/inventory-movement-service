package com.saibonthala.inventory.repository;

/** Projection: ledger balance (sum of all deltas) for one location. */
public interface LocationBalance {

    String getLocationCode();

    Long getBalance();
}
