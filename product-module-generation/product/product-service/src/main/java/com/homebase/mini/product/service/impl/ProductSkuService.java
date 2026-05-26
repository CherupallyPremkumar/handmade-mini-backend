package com.homebase.mini.product.service;

import com.homebase.mini.product.configuration.dao.ProductRepository;
import com.homebase.mini.product.model.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * SKU generation logic — ported from the deprecated {@code com.pochampally.service.ProductService}.
 * <p>
 * Called from the Chenile STM entry action ({@link com.homebase.mini.product.service.cmds.DefaultSTMTransitionAction})
 * before a new Product is persisted for the first time.
 * </p>
 *
 * <pre>
 * Format: DHN-{FABRIC_PREFIX}-{ZERO_PADDED_COUNT}
 * Example: DHN-SIL-0042
 * </pre>
 */
@Service
public class ProductSkuService {

    private static final String PREFIX = "DHN";

    @Autowired
    private ProductRepository productRepository;

    /**
     * Generate a unique SKU if one has not been provided.
     * Called during product creation (DRAFT entry).
     *
     * @param product the product being created
     */
    public void ensureSku(Product product) {
        if (product.getSku() != null && !product.getSku().isBlank()) {
            return; // SKU already set by caller — respect it
        }
        product.setSku(generateSku(product));
    }

    private String generateSku(Product product) {
        String fabricCode = product.getFabric() != null
                ? product.getFabric().name().substring(0, Math.min(3, product.getFabric().name().length()))
                : "GEN";
        long count = productRepository.count() + 1;
        return (PREFIX + "-" + fabricCode + "-" + String.format("%04d", count)).toUpperCase();
    }
}
