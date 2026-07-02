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
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class ProductController {

    @org.springframework.beans.factory.annotation.Value("${app.frontend-url:https://dhanunjaiah.com}")
    private String frontendUrl;

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

    @GetMapping(value = "/api/products/feed.xml", produces = "application/xml; charset=UTF-8")
    public ResponseEntity<String> googleProductFeed() {
        List<Product> products = productService.listActive();

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<rss version=\"2.0\" xmlns:g=\"http://base.google.com/ns/1.0\">\n");
        xml.append("<channel>\n");
        xml.append("<title>Dhanunjaiah Handlooms</title>\n");
        xml.append("<link>").append(esc(frontendUrl)).append("</link>\n");
        xml.append("<description>Authentic Handwoven Pochampally Ikat Sarees</description>\n");

        for (Product p : products) {
            if (p.getImages() == null || p.getImages().isEmpty()) continue;

            xml.append("<item>\n");
            xml.append("  <g:id>").append(esc(p.getSku())).append("</g:id>\n");
            xml.append("  <title>").append(esc(p.getName())).append("</title>\n");
            xml.append("  <description>").append(esc(desc(p))).append("</description>\n");
            xml.append("  <link>").append(esc(frontendUrl)).append("/sarees/").append(esc(p.getId())).append("</link>\n");
            xml.append("  <g:image_link>").append(esc(p.getImages().get(0))).append("</g:image_link>\n");
            for (int i = 1; i < Math.min(p.getImages().size(), 10); i++) {
                xml.append("  <g:additional_image_link>").append(esc(p.getImages().get(i))).append("</g:additional_image_link>\n");
            }
            xml.append("  <g:availability>").append(p.getStock() > 0 ? "in_stock" : "out_of_stock").append("</g:availability>\n");
            if (p.getMrp() != null && p.getMrp() > p.getSellingPrice()) {
                xml.append("  <g:price>").append(String.format("%.2f INR", p.getMrp() / 100.0)).append("</g:price>\n");
                xml.append("  <g:sale_price>").append(String.format("%.2f INR", p.getSellingPrice() / 100.0)).append("</g:sale_price>\n");
            } else {
                xml.append("  <g:price>").append(String.format("%.2f INR", p.getSellingPrice() / 100.0)).append("</g:price>\n");
            }
            xml.append("  <g:brand>Dhanunjaiah Handlooms</g:brand>\n");
            xml.append("  <g:condition>new</g:condition>\n");
            xml.append("  <g:google_product_category>5765</g:google_product_category>\n");
            xml.append("  <g:product_type>Apparel &amp; Accessories &gt; Clothing &gt; Sarees</g:product_type>\n");
            xml.append("  <g:mpn>").append(esc(p.getSku())).append("</g:mpn>\n");
            if (p.getBodyColor() != null) xml.append("  <g:color>").append(esc(p.getBodyColor())).append("</g:color>\n");
            if (p.getFabric() != null) xml.append("  <g:material>").append(esc(fabricLabel(p.getFabric()))).append("</g:material>\n");
            if (p.getPattern() != null) xml.append("  <g:pattern>").append(esc(p.getPattern())).append("</g:pattern>\n");
            xml.append("  <g:gender>Female</g:gender>\n");
            xml.append("  <g:age_group>adult</g:age_group>\n");
            xml.append("  <g:identifier_exists>false</g:identifier_exists>\n");
            xml.append("  <g:shipping>\n");
            xml.append("    <g:country>IN</g:country>\n");
            xml.append("    <g:price>0.00 INR</g:price>\n");
            xml.append("  </g:shipping>\n");
            xml.append("</item>\n");
        }

        xml.append("</channel>\n");
        xml.append("</rss>");

        return ResponseEntity.ok()
                .header("Cache-Control", "public, max-age=3600")
                .body(xml.toString());
    }

    private String desc(Product p) {
        if (p.getDescription() != null && !p.getDescription().isBlank()) {
            return p.getDescription().length() > 5000 ? p.getDescription().substring(0, 5000) : p.getDescription();
        }
        return "Handwoven " + (p.getFabric() != null ? fabricLabel(p.getFabric()) + " " : "")
                + (p.getWeaveType() != null ? p.getWeaveType().name() + " " : "") + "saree from Dhanunjaiah Handlooms";
    }

    private String fabricLabel(Product.Fabric fabric) {
        return switch (fabric) {
            case SILK -> "Silk";
            case COTTON -> "Cotton";
            case SILK_COTTON -> "Silk Cotton";
            case LINEN -> "Linen";
            case POLYESTER -> "Polyester";
            case GEORGETTE -> "Georgette";
            case CHIFFON -> "Chiffon";
        };
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
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
