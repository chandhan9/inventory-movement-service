package com.saibonthala.inventory.events;

import com.saibonthala.inventory.domain.MovementType;

import java.time.Instant;

/** Published after every committed stock movement so downstream systems can react. */
public record InventoryEvent(
        String eventId,
        MovementType type,
        String sku,
        String locationCode,
        int quantityDelta,
        int balanceAfter,
        String transactionGroup,
        Instant occurredAt) {
}
