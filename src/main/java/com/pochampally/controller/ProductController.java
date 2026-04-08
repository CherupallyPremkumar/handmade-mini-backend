package com.pochampally.controller;

import com.pochampally.entity.Product;
import com.pochampally.service.ProductService;
import com.pochampally.service.SettingsService;
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
    private final SettingsService settingsService;

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

    @GetMapping("/api/products/sku/{sku}")
    public ResponseEntity<Product> getProductBySku(@PathVariable String sku) {
        return ResponseEntity.ok(productService.getBySku(sku));
    }

    @GetMapping("/api/products/{id}/related")
    public ResponseEntity<List<Product>> relatedProducts(@PathVariable String id,
                                                          @RequestParam(defaultValue = "4") int limit) {
        return ResponseEntity.ok(productService.findRelated(id, limit));
    }

    @GetMapping("/api/products/sitemap")
    public ResponseEntity<List<java.util.Map<String, Object>>> sitemapData() {
        return ResponseEntity.ok(productService.listActive().stream()
                .map(p -> java.util.Map.<String, Object>of(
                        "id", p.getId(),
                        "sku", p.getSku() != null ? p.getSku() : "",
                        "name", p.getName(),
                        "createdTime", p.getCreatedTime().toString()
                ))
                .toList());
    }

    // --- Admin endpoints ---

    @GetMapping("/api/admin/products")
    public ResponseEntity<List<Product>> listAllProducts() {
        return ResponseEntity.ok(productService.listAll());
    }

    @PostMapping("/api/admin/products")
    public ResponseEntity<Product> createProduct(@Valid @RequestBody Product product) {
        validateProductRules(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(product));
    }

    @PutMapping("/api/admin/products/{id}")
    public ResponseEntity<Product> updateProduct(@PathVariable String id, @Valid @RequestBody Product product) {
        validateProductRules(product);
        // Media validation only when activating a product
        Product existing = productService.getById(id);
        boolean makingActive = product.getIsActive() != null && product.getIsActive() && !existing.getIsActive();
        if (makingActive) {
            int imgCount = existing.getImages() != null ? existing.getImages().size() : 0;
            int minImages = settingsService.getInt("min_product_images");
            int minVideos = settingsService.getInt("min_product_videos");
            if (imgCount < minImages) {
                throw new IllegalStateException("Minimum " + minImages + " images required (currently " + imgCount + ")");
            }
            if (minVideos > 0 && (existing.getVideoUrl() == null || existing.getVideoUrl().isBlank())) {
                throw new IllegalStateException("Minimum " + minVideos + " video required");
            }
        }
        return ResponseEntity.ok(productService.update(id, product));
    }

    private void validateProductRules(Product product) {
        if (product.getName() == null || product.getName().isBlank()) {
            throw new IllegalArgumentException("Product name is required");
        }
        int minDescLen = settingsService.getInt("min_description_length");
        if (minDescLen == 0) minDescLen = 50;
        if ("true".equals(settingsService.get("require_product_description"))) {
            if (product.getDescription() == null || product.getDescription().isBlank()) {
                throw new IllegalArgumentException("Product description is required");
            }
            if (product.getDescription().length() < minDescLen) {
                throw new IllegalArgumentException("Product description must be at least " + minDescLen + " characters");
            }
        }
        if ("true".equals(settingsService.get("require_secondary_description"))) {
            if (product.getSecondaryDescription() == null || product.getSecondaryDescription().isBlank()) {
                throw new IllegalArgumentException("Secondary description is required");
            }
            if (product.getSecondaryDescription().length() < minDescLen) {
                throw new IllegalArgumentException("Secondary description must be at least " + minDescLen + " characters");
            }
        }
    }

    @PatchMapping("/api/admin/products/{id}/toggle-active")
    public ResponseEntity<Product> toggleActive(@PathVariable String id) {
        Product existing = productService.getById(id);
        // Validate media requirements when activating
        if (!existing.getIsActive()) {
            int imgCount = existing.getImages() != null ? existing.getImages().size() : 0;
            int minImages = settingsService.getInt("min_product_images");
            int minVideos = settingsService.getInt("min_product_videos");
            if (imgCount < minImages) {
                throw new IllegalStateException("Cannot activate: minimum " + minImages + " images required (currently " + imgCount + ")");
            }
            if (minVideos > 0 && (existing.getVideoUrl() == null || existing.getVideoUrl().isBlank())) {
                throw new IllegalStateException("Cannot activate: minimum " + minVideos + " video required");
            }
        }
        return ResponseEntity.ok(productService.toggleActive(id));
    }

    @DeleteMapping("/api/admin/products/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable String id) {
        productService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
