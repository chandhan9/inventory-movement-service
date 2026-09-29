package com.saibonthala.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Immutable, append-only ledger entry. Stock levels can always be rebuilt
 * (and validated) by summing these entries per location.
 */
@Entity
@Table(name = "inventory_transaction")
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Groups the entries created by one request, e.g. both legs of a transfer. */
    @Column(name = "transaction_group", nullable = false, length = 64)
    private String transactionGroup;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private MovementType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "quantity_delta", nullable = false)
    private int quantityDelta;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InventoryTransaction() {
        // for JPA
    }

    public InventoryTransaction(String transactionGroup, String idempotencyKey, MovementType type,
                                Product product, Location location, int quantityDelta, int balanceAfter) {
        this.transactionGroup = transactionGroup;
        this.idempotencyKey = idempotencyKey;
        this.type = type;
        this.product = product;
        this.location = location;
        this.quantityDelta = quantityDelta;
        this.balanceAfter = balanceAfter;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTransactionGroup() {
        return transactionGroup;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public MovementType getType() {
        return type;
    }

    public Product getProduct() {
        return product;
    }

    public Location getLocation() {
        return location;
    }

    public int getQuantityDelta() {
        return quantityDelta;
    }

    public int getBalanceAfter() {
        return balanceAfter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
