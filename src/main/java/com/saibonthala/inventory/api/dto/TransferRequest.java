package com.saibonthala.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TransferRequest(
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 32) String fromLocationCode,
        @NotBlank @Size(max = 32) String toLocationCode,
        @NotNull @Positive Integer quantity,
        @NotBlank @Size(max = 128) String idempotencyKey) {
}
