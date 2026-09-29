package com.saibonthala.inventory.api.dto;

import java.util.List;

public record StockSummary(String sku, int totalOnHand, List<LocationStock> locations) {

    public record LocationStock(String locationCode, String locationName, int onHand) {
    }
}
