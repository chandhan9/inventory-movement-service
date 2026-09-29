package com.saibonthala.inventory.repository;

import com.saibonthala.inventory.domain.Location;
import com.saibonthala.inventory.domain.Product;
import com.saibonthala.inventory.domain.StockLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockLevelRepository extends JpaRepository<StockLevel, Long> {

    Optional<StockLevel> findByProductAndLocation(Product product, Location location);

    @Query("select s from StockLevel s join fetch s.location l where s.product = :product order by l.code")
    List<StockLevel> findAllForProduct(@Param("product") Product product);
}
