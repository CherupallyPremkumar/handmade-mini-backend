package com.pochampally.controller;

import com.pochampally.entity.Product;
import com.pochampally.entity.WishlistItem;
import com.pochampally.repository.WishlistItemRepository;
import com.pochampally.service.ProductService;
import com.pochampally.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistItemRepository wishlistRepository;
    private final ProductService productService;
    private final SettingsService settingsService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(Authentication auth) {
        List<WishlistItem> items = wishlistRepository.findByUserIdOrderByAddedAtDesc(auth.getName());
        List<Map<String, Object>> result = items.stream().map(item -> {
            try {
                Product p = productService.getById(item.getProductId());
                return Map.<String, Object>of(
                        "id", item.getId(),
                        "productId", p.getId(),
                        "name", p.getName(),
                        "sellingPrice", p.getSellingPrice(),
                        "mrp", p.getMrp(),
                        "images", p.getImages() != null ? p.getImages() : List.of(),
                        "stock", p.getStock(),
                        "isActive", p.getIsActive(),
                        "addedAt", item.getAddedAt().toString()
                );
            } catch (Exception e) {
                return Map.<String, Object>of("id", item.getId(), "productId", item.getProductId(), "name", "Product unavailable");
            }
        }).toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<?> add(@RequestBody Map<String, String> body, Authentication auth) {
        String productId = body.get("productId");
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId is required");
        }

        productService.getById(productId); // validate product exists

        if (wishlistRepository.existsByUserIdAndProductId(auth.getName(), productId)) {
            return ResponseEntity.ok(Map.of("message", "Already in wishlist"));
        }

        int maxItems = settingsService.getInt("max_wishlist_items");
        if (maxItems > 0 && wishlistRepository.countByUserId(auth.getName()) >= maxItems) {
            throw new IllegalStateException("Wishlist is full (max " + maxItems + " items)");
        }

        WishlistItem item = WishlistItem.builder()
                .userId(auth.getName())
                .productId(productId)
                .build();
        return ResponseEntity.ok(wishlistRepository.save(item));
    }

    @Transactional
    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> remove(@PathVariable String productId, Authentication auth) {
        wishlistRepository.deleteByUserIdAndProductId(auth.getName(), productId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/check/{productId}")
    public ResponseEntity<Map<String, Boolean>> check(@PathVariable String productId, Authentication auth) {
        boolean inWishlist = wishlistRepository.existsByUserIdAndProductId(auth.getName(), productId);
        return ResponseEntity.ok(Map.of("inWishlist", inWishlist));
    }
}
