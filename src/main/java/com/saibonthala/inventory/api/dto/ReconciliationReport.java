package com.saibonthala.inventory.api.dto;

import java.util.List;

/** Compares stored on-hand balances with the balance rebuilt from the ledger. */
public record ReconciliationReport(String sku, boolean consistent, List<LocationCheck> locations) {

    public record LocationCheck(String locationCode, int onHand, long ledgerBalance, boolean matches) {
    }
}
