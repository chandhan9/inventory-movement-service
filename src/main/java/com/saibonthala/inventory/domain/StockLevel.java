package com.saibonthala.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * Current on-hand quantity of one SKU at one location.
 * The {@link Version} column gives optimistic locking, so two concurrent
 * movements can never silently overwrite each other's update.
 */
@Entity
@Table(name = "stock_level")
public class StockLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "on_hand", nullable = false)
    private int onHand;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockLevel() {
        // for JPA
    }

    public StockLevel(Product product, Location location) {
        this.product = product;
        this.location = location;
        this.onHand = 0;
        this.updatedAt = Instant.now();
    }

    /**
     * Applies a signed quantity change and returns the new balance.
     * Business rule: on-hand stock can never go negative.
     */
    public int apply(int delta) {
        int next = onHand + delta;
        if (next < 0) {
            throw new InsufficientStockException(product.getSku(), location.getCode(), onHand, -delta);
        }
        this.onHand = next;
        this.updatedAt = Instant.now();
        return next;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public Location getLocation() {
        return location;
    }

    public int getOnHand() {
        return onHand;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
