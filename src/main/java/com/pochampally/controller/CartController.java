package com.pochampally.controller;

import com.pochampally.entity.CartItem;
import com.pochampally.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping("/{sessionId}")
    public ResponseEntity<List<CartItem>> getCart(@PathVariable String sessionId) {
        return ResponseEntity.ok(cartService.getCart(sessionId));
    }

    @PostMapping("/{sessionId}/add")
    public ResponseEntity<CartItem> addToCart(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> body) {

        String productId = (String) body.get("productId");
        int quantity = body.containsKey("quantity") ? ((Number) body.get("quantity")).intValue() : 1;

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cartService.addToCart(sessionId, productId, quantity));
    }

    @DeleteMapping("/{sessionId}/{itemId}")
    public ResponseEntity<Void> removeFromCart(
            @PathVariable String sessionId,
            @PathVariable String itemId) {
        cartService.removeFromCart(sessionId, itemId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> clearCart(@PathVariable String sessionId) {
        cartService.clearCart(sessionId);
        return ResponseEntity.noContent().build();
    }
}
