package com.pochampally.dto;

import com.pochampally.entity.Order;
import com.pochampally.entity.OrderItem;

import java.time.Instant;
import java.util.List;

/**
 * Public-facing DTO for order tracking that excludes PII.
 * Only exposes: orderNumber, status, trackingNumber, items (name + qty), totalAmount, createdTime.
 */
public record OrderTrackingDto(
        String orderNumber,
        String status,
        String trackingNumber,
        List<TrackingItem> items,
        Long totalAmount,
        Instant createdTime
) {
    public record TrackingItem(String productName, int quantity) {}

    public static OrderTrackingDto from(Order order) {
        List<TrackingItem> trackingItems = order.getItems().stream()
                .map(item -> new TrackingItem(item.getProductName(), item.getQuantity()))
                .toList();

        return new OrderTrackingDto(
                order.getOrderNumber(),
                order.getStatus().name(),
                order.getTrackingNumber(),
                trackingItems,
                order.getTotalAmount(),
                order.getCreatedTime()
        );
    }
}
