package com.saibonthala.inventory.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockLevelTest {

    private final Product product = new Product("SKU-1", "Test product");
    private final Location location = new Location("STORE-1", "Test store", LocationType.STORE);

    @Test
    @DisplayName("applying a positive delta increases on-hand stock")
    void positiveDeltaIncreasesStock() {
        StockLevel level = new StockLevel(product, location);

        int balance = level.apply(10);

        assertThat(balance).isEqualTo(10);
        assertThat(level.getOnHand()).isEqualTo(10);
    }

    @Test
    @DisplayName("stock can be drawn down to exactly zero")
    void canDrawDownToZero() {
        StockLevel level = new StockLevel(product, location);
        level.apply(5);

        assertThat(level.apply(-5)).isZero();
    }

    @Test
    @DisplayName("a movement that would make stock negative is rejected and leaves stock unchanged")
    void rejectsNegativeStock() {
        StockLevel level = new StockLevel(product, location);
        level.apply(3);

        assertThatThrownBy(() -> level.apply(-4))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("available 3, requested 4");
        assertThat(level.getOnHand()).isEqualTo(3);
    }
}
