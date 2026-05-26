package com.homebase.mini.product.service;

import com.homebase.mini.product.configuration.dao.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock management logic — ported from the deprecated {@code com.pochampally.service.ProductService}.
 * <p>
 * Used by the order / checkout flow to atomically adjust stock counts when
 * orders are placed or cancelled.
 * </p>
 * <p>
 * <strong>Note:</strong> These methods bypass the Chenile STM intentionally because
 * stock changes are not workflow-state changes — they are quantity adjustments
 * that happen as a side-effect of order processing.
 * </p>
 */
@Service
@Transactional
public class ProductStockService {

    @Autowired
    private ProductRepository productRepository;

    /**
     * Atomically decrement stock by {@code quantity} units.
     * Uses a conditional UPDATE so no race condition is possible.
     *
     * @param productId the product ID
     * @param quantity  how many units to consume
     * @throws IllegalStateException if stock is insufficient
     */
    public void decrementStock(String productId, int quantity) {
        int updated = productRepository.decrementStock(productId, quantity);
        if (updated == 0) {
            throw new IllegalStateException(
                    "Out of stock or insufficient quantity for product: " + productId);
        }
    }

    /**
     * Atomically increment stock (e.g. on order cancellation or return).
     *
     * @param productId the product ID
     * @param quantity  how many units to restore
     */
    public void incrementStock(String productId, int quantity) {
        productRepository.incrementStock(productId, quantity);
    }
}
