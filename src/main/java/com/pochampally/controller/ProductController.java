package com.pochampally.controller;

import com.pochampally.entity.Product;
import com.pochampally.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // --- Public endpoints ---

    @GetMapping("/api/products")
    public ResponseEntity<List<Product>> listProducts(
            @RequestParam(required = false) Product.Fabric fabric,
            @RequestParam(required = false) Product.WeaveType weaveType,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) Long minPrice,
            @RequestParam(required = false) Long maxPrice,
            @RequestParam(required = false) String search) {

        List<Product> products;

        if (search != null && !search.isBlank()) {
            products = productService.search(search);
        } else if (fabric != null) {
            products = productService.filterByFabric(fabric);
        } else if (weaveType != null) {
            products = productService.filterByWeaveType(weaveType);
        } else if (color != null) {
            products = productService.filterByColor(color);
        } else if (minPrice != null && maxPrice != null) {
            products = productService.filterByPriceRange(minPrice, maxPrice);
        } else {
            products = productService.listActive();
        }

        return ResponseEntity.ok(products);
    }

    @GetMapping("/api/products/{id}")
    public ResponseEntity<Product> getProduct(@PathVariable String id) {
        return ResponseEntity.ok(productService.getById(id));
    }

    // --- Admin endpoints ---

    @PostMapping("/api/admin/products")
    public ResponseEntity<Product> createProduct(@Valid @RequestBody Product product) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(product));
    }

    @PutMapping("/api/admin/products/{id}")
    public ResponseEntity<Product> updateProduct(@PathVariable String id, @Valid @RequestBody Product product) {
        return ResponseEntity.ok(productService.update(id, product));
    }

    @DeleteMapping("/api/admin/products/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable String id) {
        productService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
