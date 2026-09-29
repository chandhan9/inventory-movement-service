package com.saibonthala.inventory.api.dto;

import com.saibonthala.inventory.domain.LocationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateLocationRequest(
        @NotBlank @Size(max = 32) String code,
        @NotBlank @Size(max = 128) String name,
        @NotNull LocationType type) {
}
