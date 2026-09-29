package com.saibonthala.inventory.service;

import com.saibonthala.inventory.api.dto.CreateLocationRequest;
import com.saibonthala.inventory.api.dto.CreateProductRequest;
import com.saibonthala.inventory.api.dto.LocationView;
import com.saibonthala.inventory.api.dto.ProductView;
import com.saibonthala.inventory.domain.Location;
import com.saibonthala.inventory.domain.Product;
import com.saibonthala.inventory.repository.LocationRepository;
import com.saibonthala.inventory.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CatalogService {

    private final LocationRepository locationRepository;
    private final ProductRepository productRepository;

    public CatalogService(LocationRepository locationRepository, ProductRepository productRepository) {
        this.locationRepository = locationRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public LocationView createLocation(CreateLocationRequest request) {
        if (locationRepository.existsByCode(request.code())) {
            throw new ConflictException("Location " + request.code() + " already exists");
        }
        Location saved = locationRepository.save(new Location(request.code(), request.name(), request.type()));
        return LocationView.from(saved);
    }

    @Transactional(readOnly = true)
    public List<LocationView> listLocations() {
        return locationRepository.findAll().stream().map(LocationView::from).toList();
    }

    @Transactional
    public ProductView createProduct(CreateProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new ConflictException("Product " + request.sku() + " already exists");
        }
        Product saved = productRepository.save(new Product(request.sku(), request.name()));
        return ProductView.from(saved);
    }

    @Transactional(readOnly = true)
    public ProductView getProduct(String sku) {
        return productRepository.findBySku(sku)
                .map(ProductView::from)
                .orElseThrow(() -> new NotFoundException("Product " + sku + " not found"));
    }
}
