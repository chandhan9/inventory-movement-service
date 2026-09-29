package com.saibonthala.inventory.api;

import com.saibonthala.inventory.api.dto.CreateLocationRequest;
import com.saibonthala.inventory.api.dto.CreateProductRequest;
import com.saibonthala.inventory.api.dto.LocationView;
import com.saibonthala.inventory.api.dto.ProductView;
import com.saibonthala.inventory.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @PostMapping("/locations")
    @ResponseStatus(HttpStatus.CREATED)
    public LocationView createLocation(@Valid @RequestBody CreateLocationRequest request) {
        return catalogService.createLocation(request);
    }

    @GetMapping("/locations")
    public List<LocationView> listLocations() {
        return catalogService.listLocations();
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductView createProduct(@Valid @RequestBody CreateProductRequest request) {
        return catalogService.createProduct(request);
    }

    @GetMapping("/products/{sku}")
    public ProductView getProduct(@PathVariable String sku) {
        return catalogService.getProduct(sku);
    }
}
