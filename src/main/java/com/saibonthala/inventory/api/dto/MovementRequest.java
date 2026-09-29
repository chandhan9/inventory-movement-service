package com.saibonthala.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Body for receipts and sales. Quantity is always positive; the API decides the sign. */
public record MovementRequest(
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 32) String locationCode,
        @NotNull @Positive Integer quantity,
        @NotBlank @Size(max = 128) String idempotencyKey) {
}
