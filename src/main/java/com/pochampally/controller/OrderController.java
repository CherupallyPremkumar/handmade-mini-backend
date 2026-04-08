package com.pochampally.controller;

import com.pochampally.dto.OrderTrackingDto;
import com.pochampally.entity.Order;
import com.pochampally.entity.User;
import com.pochampally.service.AnalyticsService;
import com.pochampally.service.AuthService;
import com.pochampally.service.InvoiceService;
import com.pochampally.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final AnalyticsService analyticsService;
    private final AuthService authService;
    private final InvoiceService invoiceService;

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
    public ResponseEntity<?> listOrders(
            @RequestParam(required = false) Order.OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(page, Math.min(size, 100));
        if (status != null) {
            return ResponseEntity.ok(orderService.listByStatus(status, pageable));
        }
        return ResponseEntity.ok(orderService.listAll(pageable));
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

    @GetMapping("/api/orders/{orderNumber}/invoice")
    public ResponseEntity<byte[]> downloadInvoice(@PathVariable String orderNumber, Authentication auth) {
        Order order = orderService.getByOrderNumber(orderNumber);

        // Only PAID/SHIPPED/DELIVERED orders have invoices
        Set<Order.OrderStatus> invoiceStatuses = Set.of(
                Order.OrderStatus.PAID, Order.OrderStatus.SHIPPED, Order.OrderStatus.DELIVERED);
        if (!invoiceStatuses.contains(order.getStatus())) {
            throw new IllegalStateException("Invoice not available for orders with status: " + order.getStatus());
        }

        // Verify ownership (by email) or admin
        if (auth != null) {
            User user = authService.getUserById(auth.getName());
            if (user.getRole() != User.Role.ADMIN) {
                if (order.getCustomerEmail() == null || !order.getCustomerEmail().equals(user.getEmail())) {
                    throw new IllegalArgumentException("Access denied");
                }
            }
        }

        byte[] pdf = invoiceService.generateInvoicePdf(order);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=invoice-" + orderNumber + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/api/admin/analytics")
    public ResponseEntity<Map<String, Object>> analytics() {
        return ResponseEntity.ok(analyticsService.getDashboard());
    }
}
