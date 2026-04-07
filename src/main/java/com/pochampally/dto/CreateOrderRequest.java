package com.pochampally.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CreateOrderRequest {
    private String customerName;
    private String customerPhone;
    private String customerEmail;
    private Map<String, String> shippingAddress;
    private List<OrderItemRequest> items;
    private String sessionId;

    @Data
    public static class OrderItemRequest {
        private String productId;
        private int quantity;
    }
}
