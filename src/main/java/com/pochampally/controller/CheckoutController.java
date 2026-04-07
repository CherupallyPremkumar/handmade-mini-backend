package com.pochampally.controller;

import com.pochampally.dto.CreateOrderRequest;
import com.pochampally.entity.Order;
import com.pochampally.service.OrderService;
import com.pochampally.service.RazorpayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/checkout")
@RequiredArgsConstructor
@Slf4j
public class CheckoutController {

    private final OrderService orderService;
    private final RazorpayService razorpayService;

    @org.springframework.beans.factory.annotation.Value("${app.frontend-url:https://dhanunjaiah.com}")
    private String frontendUrl;

    /**
     * Step 1: Create order from cart and generate Razorpay order.
     *
     * Request body:
     * {
     *   "sessionId": "abc123",
     *   "customerName": "John Doe",
     *   "customerPhone": "9876543210",
     *   "customerEmail": "john@example.com",
     *   "shippingAddress": {
     *     "line1": "123 Main St",
     *     "line2": "Apt 4",
     *     "city": "Hyderabad",
     *     "state": "Telangana",
     *     "pincode": "500001"
     *   }
     * }
     */
    @PostMapping("/create-order")
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody CreateOrderRequest req) {
        String customerName = req.getCustomerName();
        String customerPhone = req.getCustomerPhone();
        String customerEmail = req.getCustomerEmail();
        Map<String, String> shippingAddress = req.getShippingAddress();

        if (customerName == null || customerPhone == null || shippingAddress == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Missing required fields: customerName, customerPhone, shippingAddress"));
        }

        // Input sanitization
        if (customerName.length() > 200) customerName = customerName.substring(0, 200);
        if (customerPhone.length() > 20) customerPhone = customerPhone.substring(0, 20);
        if (customerEmail != null && customerEmail.length() > 200) customerEmail = customerEmail.substring(0, 200);

        // Create order
        Order order;
        if (req.getItems() != null && !req.getItems().isEmpty()) {
            List<Map<String, Object>> items = req.getItems().stream()
                    .map(i -> {
                        java.util.HashMap<String, Object> m = new java.util.HashMap<>();
                        m.put("productId", i.getProductId());
                        m.put("quantity", i.getQuantity());
                        return (Map<String, Object>) m;
                    })
                    .collect(Collectors.toList());
            order = orderService.createOrderFromItems(items, customerName, customerPhone, customerEmail, shippingAddress);
        } else if (req.getSessionId() != null) {
            order = orderService.createOrder(req.getSessionId(), customerName, customerPhone, customerEmail, shippingAddress);
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "Provide items or sessionId"));
        }

        // Create Razorpay order + save ID in single flow
        // If this fails, the order exists but has no Razorpay ID — will be cleaned up by expiry scheduler
        String razorpayOrderId;
        try {
            razorpayOrderId = razorpayService.createRazorpayOrder(order.getTotalAmount(), order.getOrderNumber());
            orderService.setRazorpayOrderId(order.getId(), razorpayOrderId);
        } catch (Exception e) {
            log.error("Failed to create Razorpay order for {}: {}", order.getOrderNumber(), e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Payment gateway error. Please try again."));
        }

        log.info("Checkout initiated. Order: {}, Razorpay order: {}, Amount: {} paisa",
                order.getOrderNumber(), razorpayOrderId, order.getTotalAmount());

        return ResponseEntity.ok(Map.of(
                "orderNumber", order.getOrderNumber(),
                "razorpayOrderId", razorpayOrderId,
                "razorpayKeyId", razorpayService.getKeyId(),
                "amount", order.getTotalAmount(),
                "currency", "INR",
                "customerName", customerName,
                "customerEmail", customerEmail != null ? customerEmail : "",
                "customerPhone", customerPhone
        ));
    }

    /**
     * Step 2: Verify payment after Razorpay checkout completes.
     *
     * Request body:
     * {
     *   "razorpayOrderId": "order_xxx",
     *   "razorpayPaymentId": "pay_xxx",
     *   "razorpaySignature": "hmac_signature"
     * }
     */
    @PostMapping("/verify-payment")
    public ResponseEntity<Map<String, Object>> verifyPayment(@RequestBody Map<String, String> body) {
        String razorpayOrderId = body.get("razorpayOrderId");
        String razorpayPaymentId = body.get("razorpayPaymentId");
        String razorpaySignature = body.get("razorpaySignature");

        if (razorpayOrderId == null || razorpayPaymentId == null || razorpaySignature == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Missing required fields: razorpayOrderId, razorpayPaymentId, razorpaySignature"));
        }

        boolean signatureValid = razorpayService.verifyPaymentSignature(
                razorpayOrderId, razorpayPaymentId, razorpaySignature);

        if (!signatureValid) {
            log.warn("Payment verification failed for Razorpay order: {}", razorpayOrderId);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Payment verification failed. Invalid signature.",
                    "verified", false));
        }

        Order order = orderService.markAsPaid(razorpayOrderId, razorpayPaymentId);

        log.info("Payment verified. Order: {} marked as PAID. Razorpay payment: {}",
                order.getOrderNumber(), razorpayPaymentId);

        return ResponseEntity.ok(Map.of(
                "verified", true,
                "orderNumber", order.getOrderNumber(),
                "status", order.getStatus().name()));
    }

    /**
     * Razorpay redirect callback — receives POST after payment on hosted checkout.
     * Verifies signature, marks order as paid, redirects to frontend order confirmation.
     */
    @PostMapping(value = "/payment-callback", consumes = {
            org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
            org.springframework.http.MediaType.ALL_VALUE
    })
    public ResponseEntity<Void> paymentCallback(@RequestParam("razorpay_order_id") String razorpayOrderId,
                                                 @RequestParam("razorpay_payment_id") String razorpayPaymentId,
                                                 @RequestParam("razorpay_signature") String razorpaySignature) {

        boolean valid = razorpayService.verifyPaymentSignature(razorpayOrderId, razorpayPaymentId, razorpaySignature);

        if (valid) {
            Order order = orderService.markAsPaid(razorpayOrderId, razorpayPaymentId);
            log.info("Payment callback: Order {} marked PAID", order.getOrderNumber());
            String redirectUrl = frontendUrl + "/order-confirmation/" + order.getOrderNumber();
            return ResponseEntity.status(302).header("Location", redirectUrl).build();
        } else {
            log.warn("Payment callback: Invalid signature for {}", razorpayOrderId);
            return ResponseEntity.status(302).header("Location", frontendUrl + "/checkout?error=payment_failed").build();
        }
    }
}
