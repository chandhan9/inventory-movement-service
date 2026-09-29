package com.saibonthala.inventory.repository;

import com.saibonthala.inventory.domain.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    Optional<Location> findByCode(String code);

    boolean existsByCode(String code);
}
