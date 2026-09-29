package com.saibonthala.inventory.repository;

import com.saibonthala.inventory.domain.InventoryTransaction;
import com.saibonthala.inventory.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    @Query("select t from InventoryTransaction t join fetch t.location join fetch t.product "
            + "where t.idempotencyKey = :key order by t.id")
    List<InventoryTransaction> findByIdempotencyKey(@Param("key") String key);

    @Query("select t from InventoryTransaction t join fetch t.location where t.product = :product order by t.id")
    List<InventoryTransaction> findLedger(@Param("product") Product product);

    @Query("select t.location.code as locationCode, sum(t.quantityDelta) as balance "
            + "from InventoryTransaction t where t.product = :product group by t.location.code")
    List<LocationBalance> sumByLocation(@Param("product") Product product);
}
