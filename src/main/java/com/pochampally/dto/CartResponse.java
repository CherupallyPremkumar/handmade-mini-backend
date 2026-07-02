package com.pochampally.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponse {

    private List<CartItemView> items;

    /** Sum of (currentPrice × quantity) for all in-stock, active items. */
    private long subtotal;

    /** Sum of (snapshotPrice × quantity) — the total user originally saw. */
    private long snapshotSubtotal;

    /** True if any item has a price difference vs snapshot. */
    private boolean hasPriceChanges;

    /** True if any item is now out of stock or has insufficient stock. */
    private boolean hasStockIssues;

    /** True if any item's product has been deactivated or deleted. */
    private boolean hasUnavailableItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartItemView {
        private String id;
        private String productId;
        private String name;
        private String image;
        private String sku;
        private Integer quantity;
        private Integer availableStock;
        private Long currentPrice;
        private Long currentMrp;
        private Long snapshotPrice;
        private Long snapshotMrp;
        private boolean priceChanged;
        private boolean inStock;
        private boolean available;
        /** Difference: positive = price went up, negative = price dropped, 0 = unchanged. */
        private long priceDifference;
    }
}
