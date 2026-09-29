package com.saibonthala.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body for cycle-count adjustments. Quantity is signed (e.g. -3 for shrink) and must not be zero. */
public record AdjustmentRequest(
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 32) String locationCode,
        @NotNull Integer quantity,
        @NotBlank @Size(max = 128) String idempotencyKey) {
}
