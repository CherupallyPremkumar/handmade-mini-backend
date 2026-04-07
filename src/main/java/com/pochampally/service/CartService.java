package com.pochampally.service;

import com.pochampally.entity.CartItem;
import com.pochampally.entity.Product;
import com.pochampally.repository.CartItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductService productService;

    public List<CartItem> getCart(String sessionId) {
        return cartItemRepository.findBySessionId(sessionId);
    }

    @Transactional
    public CartItem addToCart(String sessionId, String productId, int quantity) {
        final int qty = quantity <= 0 ? 1 : quantity;

        Product product = productService.getById(productId);
        if (!product.getIsActive()) {
            throw new IllegalArgumentException("Product is not available: " + productId);
        }
        if (product.getStock() < qty) {
            throw new IllegalStateException("Insufficient stock for " + product.getName()
                    + ". Available: " + product.getStock());
        }

        return cartItemRepository.findBySessionIdAndProductId(sessionId, productId)
                .map(existing -> {
                    int newQty = existing.getQuantity() + qty;
                    if (product.getStock() < newQty) {
                        throw new IllegalStateException("Insufficient stock for " + product.getName()
                                + ". Available: " + product.getStock() + ", Cart total would be: " + newQty);
                    }
                    existing.setQuantity(newQty);
                    return cartItemRepository.save(existing);
                })
                .orElseGet(() -> {
                    CartItem item = CartItem.builder()
                            .sessionId(sessionId)
                            .productId(productId)
                            .quantity(qty)
                            .build();
                    return cartItemRepository.save(item);
                });
    }

    @Transactional
    public void removeFromCart(String sessionId, String itemId) {
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found: " + itemId));
        if (!item.getSessionId().equals(sessionId)) {
            throw new IllegalArgumentException("Cart item does not belong to this session");
        }
        cartItemRepository.delete(item);
    }

    @Transactional
    public void clearCart(String sessionId) {
        cartItemRepository.deleteBySessionId(sessionId);
    }
}
