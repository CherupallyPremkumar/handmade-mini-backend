package com.pochampally.service;

import com.pochampally.dto.CartResponse;
import com.pochampally.entity.CartItem;
import com.pochampally.entity.Product;
import com.pochampally.repository.CartItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductService productService;

    public List<CartItem> getCart(String sessionId) {
        return cartItemRepository.findBySessionId(sessionId);
    }

    /**
     * Build the rich cart response with current prices, snapshot comparison,
     * stock status, and availability flags.
     */
    public CartResponse buildCartResponse(String sessionId) {
        List<CartItem> cartItems = cartItemRepository.findBySessionId(sessionId);
        List<CartResponse.CartItemView> views = new ArrayList<>();

        long subtotal = 0;
        long snapshotSubtotal = 0;
        boolean anyPriceChange = false;
        boolean anyStockIssue = false;
        boolean anyUnavailable = false;

        for (CartItem item : cartItems) {
            Product product;
            try {
                product = productService.getById(item.getProductId());
            } catch (IllegalArgumentException e) {
                // Product was hard-deleted — show item as unavailable
                views.add(CartResponse.CartItemView.builder()
                        .id(item.getId())
                        .productId(item.getProductId())
                        .name("(Product no longer available)")
                        .quantity(item.getQuantity())
                        .snapshotPrice(item.getSnapshotPrice())
                        .snapshotMrp(item.getSnapshotMrp())
                        .available(false)
                        .inStock(false)
                        .priceChanged(false)
                        .build());
                anyUnavailable = true;
                continue;
            }

            boolean available = Boolean.TRUE.equals(product.getIsActive())
                    && !Boolean.TRUE.equals(product.getIsDeleted());
            boolean inStock = product.getStock() != null && product.getStock() >= item.getQuantity();
            long currentPrice = product.getSellingPrice();
            long snapshotPrice = item.getSnapshotPrice() != null ? item.getSnapshotPrice() : currentPrice;
            boolean priceChanged = item.getSnapshotPrice() != null && currentPrice != snapshotPrice;
            long priceDifference = currentPrice - snapshotPrice;

            String firstImage = (product.getImages() != null && !product.getImages().isEmpty())
                    ? product.getImages().get(0)
                    : null;

            views.add(CartResponse.CartItemView.builder()
                    .id(item.getId())
                    .productId(item.getProductId())
                    .name(product.getName())
                    .image(firstImage)
                    .sku(product.getSku())
                    .quantity(item.getQuantity())
                    .availableStock(product.getStock())
                    .currentPrice(currentPrice)
                    .currentMrp(product.getMrp())
                    .snapshotPrice(snapshotPrice)
                    .snapshotMrp(item.getSnapshotMrp())
                    .priceChanged(priceChanged)
                    .priceDifference(priceDifference)
                    .inStock(inStock)
                    .available(available)
                    .build());

            if (available && inStock) {
                subtotal += currentPrice * item.getQuantity();
                snapshotSubtotal += snapshotPrice * item.getQuantity();
            }
            if (priceChanged) anyPriceChange = true;
            if (!inStock) anyStockIssue = true;
            if (!available) anyUnavailable = true;
        }

        return CartResponse.builder()
                .items(views)
                .subtotal(subtotal)
                .snapshotSubtotal(snapshotSubtotal)
                .hasPriceChanges(anyPriceChange)
                .hasStockIssues(anyStockIssue)
                .hasUnavailableItems(anyUnavailable)
                .build();
    }

    /**
     * Refresh price snapshots to current values for all items in the cart.
     * Called when user explicitly accepts updated prices.
     */
    @Transactional
    public CartResponse acceptPriceChanges(String sessionId) {
        List<CartItem> items = cartItemRepository.findBySessionId(sessionId);
        for (CartItem item : items) {
            try {
                Product product = productService.getById(item.getProductId());
                item.setSnapshotPrice(product.getSellingPrice());
                item.setSnapshotMrp(product.getMrp());
                cartItemRepository.save(item);
            } catch (IllegalArgumentException ignored) {
                // Product no longer exists; skip
            }
        }
        log.info("Cart price snapshots refreshed for session {}", sessionId);
        return buildCartResponse(sessionId);
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
                    // Refresh snapshot to current price (user is interacting with the item now)
                    existing.setSnapshotPrice(product.getSellingPrice());
                    existing.setSnapshotMrp(product.getMrp());
                    return cartItemRepository.save(existing);
                })
                .orElseGet(() -> {
                    CartItem item = CartItem.builder()
                            .sessionId(sessionId)
                            .productId(productId)
                            .quantity(qty)
                            .snapshotPrice(product.getSellingPrice())
                            .snapshotMrp(product.getMrp())
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
