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

    private static final long FREE_SHIPPING_THRESHOLD = 99900L;
    private static final long SHIPPING_COST = 9900L;
    private static final int PAYMENT_TIMEOUT_MINUTES = 30;

    /**
     * Step 1: Create order with PENDING_PAYMENT status.
     * Stock is NOT decremented — only validated.
     * Stock gets decremented only when payment is confirmed.
     */
    private static final int MAX_PENDING_ORDERS = 3;

    @Transactional
    public Order createOrderFromItems(List<Map<String, Object>> items, String customerName,
                                      String customerPhone, String customerEmail, Map<String, String> shippingAddress) {
        if (items == null || items.isEmpty()) {
            throw new IllegalStateException("No items provided");
        }

        // Prevent spam: max 3 pending orders per customer
        if (customerEmail != null) {
            long pending = orderRepository.countByCustomerEmailAndStatus(customerEmail, Order.OrderStatus.PENDING_PAYMENT);
            if (pending >= MAX_PENDING_ORDERS) {
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
            gstAmount += (itemTotal * product.getGstPct()) / 100;

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

        long shippingCost = subtotal >= FREE_SHIPPING_THRESHOLD ? 0 : SHIPPING_COST;
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
            gstAmount += (itemTotal * product.getGstPct()) / 100;

            OrderItem orderItem = OrderItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .quantity(cartItem.getQuantity())
                    .unitPrice(product.getSellingPrice())
                    .totalPrice(itemTotal)
                    .build();

            order.addItem(orderItem);
        }

        long shippingCost = subtotal >= FREE_SHIPPING_THRESHOLD ? 0 : SHIPPING_COST;
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

        // Decrement stock for each item — if any fails, mark for manual refund
        try {
            for (OrderItem item : order.getItems()) {
                productService.decrementStock(item.getProductId(), item.getQuantity());
            }
        } catch (IllegalStateException e) {
            // Stock exhausted after payment captured — needs manual refund
            log.error("STOCK EXHAUSTED after payment! Order: {}, Razorpay: {}. MANUAL REFUND NEEDED.",
                    order.getOrderNumber(), razorpayPaymentId);
            order.setRazorpayPaymentId(razorpayPaymentId);
            order.setPaymentStatus("captured_stock_exhausted");
            order.setStatus(Order.OrderStatus.CANCELLED);
            return orderRepository.save(order);
        }

        order.setRazorpayPaymentId(razorpayPaymentId);
        order.setPaymentStatus("captured");
        order.setStatus(Order.OrderStatus.PAID);

        log.info("Order {} marked PAID. Stock decremented for {} items.",
                order.getOrderNumber(), order.getItems().size());

        return orderRepository.save(order);
    }

    @Transactional
    public Order markPaymentFailed(String razorpayOrderId) {
        Order order = getByRazorpayOrderId(razorpayOrderId);
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
        Instant cutoff = Instant.now().minus(PAYMENT_TIMEOUT_MINUTES, ChronoUnit.MINUTES);
        List<Order> expired = orderRepository.findByStatusAndCreatedTimeBefore(
                Order.OrderStatus.PENDING_PAYMENT, cutoff);

        for (Order order : expired) {
            order.setStatus(Order.OrderStatus.CANCELLED);
            order.setPaymentStatus("expired");
            orderRepository.save(order);
            log.info("Expired order {} cancelled (no payment after {} min)",
                    order.getOrderNumber(), PAYMENT_TIMEOUT_MINUTES);
        }

        if (!expired.isEmpty()) {
            log.info("Cancelled {} expired orders", expired.size());
        }
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

    public List<Order> listByStatus(Order.OrderStatus status) {
        return orderRepository.findByStatus(status);
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
