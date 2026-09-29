package com.saibonthala.inventory.api.dto;

import com.saibonthala.inventory.domain.InventoryTransaction;
import com.saibonthala.inventory.domain.MovementType;

import java.time.Instant;

public record TransactionView(
        Long id,
        String transactionGroup,
        MovementType type,
        String locationCode,
        int quantityDelta,
        int balanceAfter,
        Instant createdAt) {

    public static TransactionView from(InventoryTransaction tx) {
        return new TransactionView(tx.getId(), tx.getTransactionGroup(), tx.getType(),
                tx.getLocation().getCode(), tx.getQuantityDelta(), tx.getBalanceAfter(), tx.getCreatedAt());
    }
}
