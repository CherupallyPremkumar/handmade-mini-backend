package com.pochampally.service;

import com.pochampally.entity.*;
import com.pochampally.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final ProductService productService;
    private final RazorpayService razorpayService;
    private final SettingsService settingsService;
    private final EmailService emailService;
    private final CouponService couponService;

    // Fallback only — real value from settingsService.getInt("payment_timeout_minutes")

    @Transactional
    public Order createOrderFromItems(List<Map<String, Object>> items, String customerName,
                                      String customerPhone, String customerEmail, Map<String, String> shippingAddress) {
        if (items == null || items.isEmpty()) {
            throw new IllegalStateException("No items provided");
        }

        // Prevent spam: max pending orders per customer (by email or phone)
        int maxPending = settingsService.getInt("max_pending_orders");
        long pendingByPhone = orderRepository.countByCustomerPhoneAndStatus(customerPhone, Order.OrderStatus.PENDING_PAYMENT);
        if (pendingByPhone >= maxPending) {
            throw new IllegalStateException("Too many pending orders. Complete or wait for existing orders to expire.");
        }
        if (customerEmail != null) {
            long pendingByEmail = orderRepository.countByCustomerEmailAndStatus(customerEmail, Order.OrderStatus.PENDING_PAYMENT);
            if (pendingByEmail >= maxPending) {
                throw new IllegalStateException("Too many pending orders. Complete or wait for existing orders to expire.");
            }
        }

        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .customerName(customerName)
                .customerPhone(customerPhone)
                .customerEmail(customerEmail)
                .shippingAddress(shippingAddress)
                .status(Order.OrderStatus.PENDING_PAYMENT)
                .createdTime(Instant.now())
                .build();

        long subtotal = 0;
        long gstAmount = 0;

        for (Map<String, Object> item : items) {
            String productId = (String) item.get("productId");
            if (productId == null) productId = (String) item.get("sareeId");
            if (productId == null) {
                throw new IllegalArgumentException("Item missing productId");
            }
            int qty = item.get("quantity") instanceof Number ? ((Number) item.get("quantity")).intValue() : 1;
            if (qty <= 0) {
                throw new IllegalArgumentException("Item quantity must be positive");
            }

            Product product = productService.getById(productId);

            // Validate stock but don't decrement — decrement happens on payment confirmation
            if (product.getStock() < qty) {
                throw new IllegalStateException("Insufficient stock for " + product.getName());
            }

            long itemTotal = product.getSellingPrice() * qty;
            subtotal += itemTotal;
            gstAmount += Math.round((itemTotal * product.getGstPct()) / 100.0);

            OrderItem orderItem = OrderItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .quantity(qty)
                    .unitPrice(product.getSellingPrice())
                    .totalPrice(itemTotal)
                    .build();
            orderItem.setOrder(order);
            order.getItems().add(orderItem);
        }

        long shippingCost = subtotal >= settingsService.getLong("free_shipping_threshold") ? 0 : settingsService.getLong("shipping_cost");
        long totalAmount = subtotal + gstAmount + shippingCost;

        order.setSubtotal(subtotal);
        order.setGstAmount(gstAmount);
        order.setShippingCost(shippingCost);
        order.setTotalAmount(totalAmount);

        return orderRepository.save(order);
    }

    @Transactional
    public Order createOrder(String sessionId, String customerName, String customerPhone,
                             String customerEmail, Map<String, String> shippingAddress) {
        List<CartItem> cartItems = cartService.getCart(sessionId);
        if (cartItems.isEmpty()) {
            throw new IllegalStateException("Cart is empty");
        }

        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .customerName(customerName)
                .customerPhone(customerPhone)
                .customerEmail(customerEmail)
                .shippingAddress(shippingAddress)
                .status(Order.OrderStatus.PENDING_PAYMENT)
                .createdTime(Instant.now())
                .build();

        long subtotal = 0;
        long gstAmount = 0;

        for (CartItem cartItem : cartItems) {
            Product product = productService.getById(cartItem.getProductId());

            if (product.getStock() < cartItem.getQuantity()) {
                throw new IllegalStateException("Insufficient stock for " + product.getName());
            }

            long itemTotal = product.getSellingPrice() * cartItem.getQuantity();
            subtotal += itemTotal;
            gstAmount += Math.round((itemTotal * product.getGstPct()) / 100.0);

            OrderItem orderItem = OrderItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .quantity(cartItem.getQuantity())
                    .unitPrice(product.getSellingPrice())
                    .totalPrice(itemTotal)
                    .build();

            order.addItem(orderItem);
        }

        long shippingCost = subtotal >= settingsService.getLong("free_shipping_threshold") ? 0 : settingsService.getLong("shipping_cost");
        long totalAmount = subtotal + gstAmount + shippingCost;

        order.setSubtotal(subtotal);
        order.setGstAmount(gstAmount);
        order.setShippingCost(shippingCost);
        order.setTotalAmount(totalAmount);

        return orderRepository.save(order);
    }

    /**
     * Step 2: Payment confirmed — decrement stock atomically, change status to PAID.
     * Uses pessimistic lock to prevent race condition between webhook and callback.
     * Idempotent — if already PAID, returns existing order.
     * If stock decrement fails, marks order as STOCK_EXHAUSTED (needs manual refund).
     */
    @Transactional
    public Order markAsPaid(String razorpayOrderId, String razorpayPaymentId) {
        // Pessimistic lock — only one thread can process this order at a time
        Order order = orderRepository.findByRazorpayOrderIdForUpdate(razorpayOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found for Razorpay order: " + razorpayOrderId));

        // Idempotent: already paid, return as-is
        if (order.getStatus() == Order.OrderStatus.PAID) {
            return order;
        }

        // Only PENDING_PAYMENT or PLACED can transition to PAID
        if (order.getStatus() != Order.OrderStatus.PENDING_PAYMENT &&
            order.getStatus() != Order.OrderStatus.PLACED) {
            throw new IllegalStateException("Cannot mark order " + order.getOrderNumber() +
                    " as paid — current status: " + order.getStatus());
        }

        // Decrement stock for each item — if any fails, restore already-decremented and refund
        List<OrderItem> decremented = new java.util.ArrayList<>();
        try {
            for (OrderItem item : order.getItems()) {
                productService.decrementStock(item.getProductId(), item.getQuantity());
                decremented.add(item);
            }
        } catch (IllegalStateException e) {
            log.error("STOCK EXHAUSTED after payment! Order: {}, Razorpay: {}. Restoring stock and initiating auto-refund.",
                    order.getOrderNumber(), razorpayPaymentId);

            // Restore stock for items that were already decremented
            for (OrderItem restored : decremented) {
                try {
                    productService.incrementStock(restored.getProductId(), restored.getQuantity());
                } catch (Exception restoreEx) {
                    log.error("Failed to restore stock for product {} in order {}",
                            restored.getProductId(), order.getOrderNumber(), restoreEx);
                }
            }

            order.setRazorpayPaymentId(razorpayPaymentId);
            order.setStatus(Order.OrderStatus.CANCELLED);

            // Auto-refund via Razorpay
            try {
                String refundId = razorpayService.refundPayment(razorpayPaymentId, order.getTotalAmount(),
                        "Stock exhausted after payment for order " + order.getOrderNumber());
                order.setPaymentStatus("refunded");
                log.info("Auto-refund successful for order {}. Refund ID: {}", order.getOrderNumber(), refundId);
            } catch (Exception refundEx) {
                order.setPaymentStatus("captured_stock_exhausted_refund_failed");
                log.error("AUTO-REFUND FAILED for order {}. MANUAL REFUND NEEDED. Payment: {}",
                        order.getOrderNumber(), razorpayPaymentId, refundEx);
            }
            return orderRepository.save(order);
        }

        order.setRazorpayPaymentId(razorpayPaymentId);
        order.setPaymentStatus("captured");
        order.setStatus(Order.OrderStatus.PAID);

        log.info("Order {} marked PAID. Stock decremented for {} items.",
                order.getOrderNumber(), order.getItems().size());

        Order saved = orderRepository.save(order);

        // Record coupon usage (non-blocking)
        if (saved.getCouponCode() != null && !saved.getCouponCode().isBlank()) {
            try {
                couponService.recordUsage(saved.getCouponCode(),
                        saved.getCustomerEmail() != null ? saved.getCustomerEmail() : "unknown",
                        saved.getId());
            } catch (Exception e) {
                log.warn("Failed to record coupon usage for {}: {}", saved.getOrderNumber(), e.getMessage());
            }
        }

        // Send order emails (non-blocking)
        try {
            if ("true".equals(settingsService.get("send_order_confirmation_email"))) {
                emailService.sendOrderConfirmationEmail(saved);
            }
            String adminEmail = settingsService.get("admin_order_alert_email");
            if (!adminEmail.isBlank()) {
                emailService.sendAdminOrderAlertEmail(adminEmail, saved);
            }
        } catch (Exception e) {
            log.warn("Failed to send order emails for {}: {}", saved.getOrderNumber(), e.getMessage());
        }

        return saved;
    }

    @Transactional
    public Order markPaymentFailed(String razorpayOrderId) {
        Order order = getByRazorpayOrderId(razorpayOrderId);

        // Don't overwrite if already PAID, SHIPPED, DELIVERED, or CANCELLED
        if (order.getStatus() != Order.OrderStatus.PENDING_PAYMENT &&
            order.getStatus() != Order.OrderStatus.PLACED) {
            log.info("Order {} already in status {}, skipping markPaymentFailed",
                    order.getOrderNumber(), order.getStatus());
            return order;
        }

        order.setPaymentStatus("failed");
        order.setStatus(Order.OrderStatus.CANCELLED);
        log.info("Order {} payment failed, cancelled.", order.getOrderNumber());
        return orderRepository.save(order);
    }

    /**
     * Scheduled: Cancel expired PENDING_PAYMENT orders (>30 min).
     * No stock to release since stock wasn't decremented.
     */
    @Scheduled(fixedRate = 5 * 60 * 1000) // every 5 minutes
    @Transactional
    public void cancelExpiredOrders() {
        int timeoutMinutes = settingsService.getInt("payment_timeout_minutes");
        if (timeoutMinutes <= 0) timeoutMinutes = 30;
        Instant cutoff = Instant.now().minus(timeoutMinutes, ChronoUnit.MINUTES);
        List<Order> expired = orderRepository.findByStatusAndCreatedTimeBefore(
                Order.OrderStatus.PENDING_PAYMENT, cutoff);

        for (Order order : expired) {
            order.setStatus(Order.OrderStatus.CANCELLED);
            order.setPaymentStatus("expired");
            orderRepository.save(order);
            log.info("Expired order {} cancelled (no payment after {} min)",
                    order.getOrderNumber(), timeoutMinutes);
        }

        if (!expired.isEmpty()) {
            log.info("Cancelled {} expired orders", expired.size());
        }
    }

    /**
     * Check for a recent duplicate pending order (same phone, within 60 seconds, with Razorpay ID).
     * Prevents double-click / network retry from creating duplicate orders.
     */
    public Order findRecentDuplicate(String customerPhone) {
        Instant since = Instant.now().minus(60, ChronoUnit.SECONDS);
        List<Order> recent = orderRepository.findRecentPendingByPhone(customerPhone, since);
        return recent.isEmpty() ? null : recent.getFirst();
    }

    public Order getByOrderNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderNumber));
    }

    public Order getByRazorpayOrderId(String razorpayOrderId) {
        return orderRepository.findByRazorpayOrderId(razorpayOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found for Razorpay order: " + razorpayOrderId));
    }

    public List<Order> listAll() {
        return orderRepository.findAll();
    }

    public org.springframework.data.domain.Page<Order> listAll(org.springframework.data.domain.Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    public List<Order> listByStatus(Order.OrderStatus status) {
        return orderRepository.findByStatus(status);

    }

    public org.springframework.data.domain.Page<Order> listByStatus(Order.OrderStatus status, org.springframework.data.domain.Pageable pageable) {
        return orderRepository.findByStatus(status, pageable);
    }

    public List<Order> listByCustomerEmail(String email) {
        return orderRepository.findByCustomerEmail(email);
    }

    @Transactional
    public Order updateStatus(String id, Order.OrderStatus newStatus, String trackingNumber) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + id));

        validateStatusTransition(order.getStatus(), newStatus);
        order.setStatus(newStatus);

        if (trackingNumber != null && !trackingNumber.isBlank()) {
            order.setTrackingNumber(trackingNumber);
        }

        Order saved = orderRepository.save(order);

        // Send status update emails (non-blocking)
        try {
            if (newStatus == Order.OrderStatus.SHIPPED && "true".equals(settingsService.get("send_shipping_update_email"))) {
                emailService.sendShippingUpdateEmail(saved);
            } else if (newStatus == Order.OrderStatus.DELIVERED) {
                emailService.sendDeliveryConfirmationEmail(saved);
            }
        } catch (Exception e) {
            log.warn("Failed to send status email for {}: {}", saved.getOrderNumber(), e.getMessage());
        }

        return saved;
    }

    @Transactional
    public Order applyCoupon(String orderId, String couponCode, long discountAmount, long newTotal) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        order.setCouponCode(couponCode);
        order.setDiscountAmount(discountAmount);
        order.setTotalAmount(newTotal);
        return orderRepository.save(order);
    }

    @Transactional
    public Order setRazorpayOrderId(String orderId, String razorpayOrderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        order.setRazorpayOrderId(razorpayOrderId);
        return orderRepository.save(order);
    }

    private void validateStatusTransition(Order.OrderStatus current, Order.OrderStatus target) {
        boolean valid = switch (current) {
            case PENDING_PAYMENT -> target == Order.OrderStatus.PAID || target == Order.OrderStatus.CANCELLED;
            case PLACED -> target == Order.OrderStatus.PAID || target == Order.OrderStatus.CANCELLED;
            case PAID -> target == Order.OrderStatus.SHIPPED || target == Order.OrderStatus.CANCELLED;
            case SHIPPED -> target == Order.OrderStatus.DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };

        if (!valid) {
            throw new IllegalStateException("Invalid status transition from " + current + " to " + target);
        }
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private String generateOrderNumber() {
        String random10 = randomAlphanumeric(10);
        return "DHN-" + random10;
    }

    private String randomAlphanumeric(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(SECURE_RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }
}
