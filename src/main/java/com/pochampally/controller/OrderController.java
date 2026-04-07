package com.pochampally.controller;

import com.pochampally.dto.OrderTrackingDto;
import com.pochampally.entity.Order;
import com.pochampally.entity.User;
import com.pochampally.service.AuthService;
import com.pochampally.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final AuthService authService;

    // --- Public: Track by order number (PII hidden) ---

    @GetMapping("/api/orders/{orderNumber}")
    public ResponseEntity<OrderTrackingDto> trackOrder(@PathVariable String orderNumber) {
        Order order = orderService.getByOrderNumber(orderNumber);
        return ResponseEntity.ok(OrderTrackingDto.from(order));
    }

    // --- Authenticated: My orders ---

    @GetMapping("/api/orders/my/list")
    public ResponseEntity<List<OrderTrackingDto>> myOrders(Authentication authentication) {
        String userId = authentication.getName();
        User user = authService.getUserById(userId);
        List<Order> orders = orderService.listByCustomerEmail(user.getEmail());
        List<OrderTrackingDto> dtos = orders.stream().map(OrderTrackingDto::from).toList();
        return ResponseEntity.ok(dtos);
    }

    // --- Admin endpoints ---

    @GetMapping("/api/admin/orders")
    public ResponseEntity<List<Order>> listOrders(
            @RequestParam(required = false) Order.OrderStatus status) {
        if (status != null) {
            return ResponseEntity.ok(orderService.listByStatus(status));
        }
        return ResponseEntity.ok(orderService.listAll());
    }

    @PatchMapping("/api/admin/orders/{id}/status")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {

        String statusStr = body.get("status");
        String trackingNumber = body.get("trackingNumber");

        if (statusStr == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required field: status"));
        }

        Order.OrderStatus newStatus;
        try {
            newStatus = Order.OrderStatus.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid status: " + statusStr));
        }

        Order updated = orderService.updateStatus(id, newStatus, trackingNumber);
        return ResponseEntity.ok(updated);
    }
}
