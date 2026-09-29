package com.saibonthala.inventory.api.dto;

import com.saibonthala.inventory.domain.Product;

public record ProductView(String sku, String name) {

    public static ProductView from(Product product) {
        return new ProductView(product.getSku(), product.getName());
    }
}
