package com.saibonthala.inventory.api.dto;

import java.util.List;

/**
 * Result of a stock movement. {@code replayed} is true when the same idempotency key
 * was already processed, so the original result is returned and nothing is applied twice.
 */
public record MovementResult(String transactionGroup, String sku, boolean replayed,
                             List<TransactionView> transactions) {
}
