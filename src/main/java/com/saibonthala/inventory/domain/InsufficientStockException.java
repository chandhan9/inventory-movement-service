package com.saibonthala.inventory.domain;

/** Thrown when a movement would take on-hand stock below zero. */
public class InsufficientStockException extends RuntimeException {

    private final String sku;
    private final String locationCode;
    private final int available;
    private final int requested;

    public InsufficientStockException(String sku, String locationCode, int available, int requested) {
        super("Insufficient stock for SKU " + sku + " at " + locationCode
                + ": available " + available + ", requested " + requested);
        this.sku = sku;
        this.locationCode = locationCode;
        this.available = available;
        this.requested = requested;
    }

    public String getSku() {
        return sku;
    }

    public String getLocationCode() {
        return locationCode;
    }

    public int getAvailable() {
        return available;
    }

    public int getRequested() {
        return requested;
    }
}
