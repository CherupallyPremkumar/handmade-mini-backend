package com.pochampally.controller;

import com.pochampally.config.RateLimiter;
import com.pochampally.dto.CreateOrderRequest;
import com.pochampally.entity.Address;
import com.pochampally.entity.Order;
import com.pochampally.entity.User;
import com.pochampally.service.AddressService;
import com.pochampally.service.AuthService;
import com.pochampally.service.OrderService;
import com.pochampally.service.RazorpayService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/checkout")
@Slf4j
public class CheckoutController {

    private final OrderService orderService;
    private final RazorpayService razorpayService;
    private final AuthService authService;
    private final AddressService addressService;
    private final com.pochampally.service.CouponService couponService;
    private final RateLimiter paymentVerifyRateLimiter;

    @Value("${app.frontend-url:https://dhanunjaiah.com}")
    private String frontendUrl;

    public CheckoutController(OrderService orderService, RazorpayService razorpayService,
                               AuthService authService, AddressService addressService,
                               com.pochampally.service.CouponService couponService) {
        this.orderService = orderService;
        this.razorpayService = razorpayService;
        this.authService = authService;
        this.addressService = addressService;
        this.couponService = couponService;
        this.paymentVerifyRateLimiter = new RateLimiter();
    }

    /** Visible for test cleanup. */
    public RateLimiter getPaymentVerifyRateLimiter() {
        return paymentVerifyRateLimiter;
    }

    @PostMapping("/create-order")
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody CreateOrderRequest req,
                                                            Authentication authentication) {
        if (authentication != null) {
            User user = authService.getUserById(authentication.getName());
            if (!user.getEmailVerified()) {
                return ResponseEntity.status(403).body(Map.of(
                        "error", "Please verify your email before placing an order.",
                        "emailVerified", false));
            }
        }

        String customerName = req.getCustomerName();
        String customerPhone = req.getCustomerPhone();
        String customerEmail = req.getCustomerEmail();
        Map<String, String> shippingAddress = req.getShippingAddress();

        // Resolve from saved address
        if (req.getAddressId() != null && !req.getAddressId().isBlank() && authentication != null) {
            Address saved = addressService.getByIdAndUser(req.getAddressId(), authentication.getName());
            customerName = saved.getName();
            customerPhone = saved.getPhone();
            shippingAddress = Map.of(
                    "line1", saved.getLine1(),
                    "line2", saved.getLine2() != null ? saved.getLine2() : "",
                    "city", saved.getCity(),
                    "state", saved.getState(),
                    "pincode", saved.getPincode()
            );
        }

        if (customerName == null || customerPhone == null || shippingAddress == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Missing required fields: customerName, customerPhone, shippingAddress or addressId"));
        }

        // Validate shipping address
        String addrLine1 = shippingAddress.get("line1");
        String addrCity = shippingAddress.get("city");
        String addrState = shippingAddress.get("state");
        String addrPincode = shippingAddress.get("pincode");
        if (addrLine1 == null || addrLine1.isBlank() ||
            addrCity == null || addrCity.isBlank() ||
            addrState == null || addrState.isBlank() ||
            addrPincode == null || addrPincode.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Shipping address must include: line1, city, state, pincode"));
        }
        if (!addrPincode.matches("^[0-9]{6}$")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid pincode. Must be 6 digits."));
        }
        if (addrLine1.length() > 500 || addrCity.length() > 100 || addrState.length() > 100) {
            return ResponseEntity.badRequest().body(Map.of("error", "Address fields exceed maximum length"));
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
                        HashMap<String, Object> m = new HashMap<>();
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

        // Apply coupon if provided
        long finalAmount = order.getTotalAmount();
        if (req.getCouponCode() != null && !req.getCouponCode().isBlank() && authentication != null) {
            var couponResult = couponService.validate(req.getCouponCode(), order.getTotalAmount(), authentication.getName());
            if (Boolean.TRUE.equals(couponResult.get("valid"))) {
                long discount = ((Number) couponResult.get("discountAmount")).longValue();
                finalAmount = order.getTotalAmount() - discount;
                orderService.applyCoupon(order.getId(), req.getCouponCode(), discount, finalAmount);
            }
        }

        String razorpayOrderId;
        try {
            razorpayOrderId = razorpayService.createRazorpayOrder(finalAmount, order.getOrderNumber());
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

    @PostMapping("/verify-payment")
    public ResponseEntity<Map<String, Object>> verifyPayment(@RequestBody Map<String, String> body,
                                                              HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        if (!paymentVerifyRateLimiter.tryAcquire(ip)) {
            return ResponseEntity.status(429).body(Map.of(
                    "error", "Too many verification attempts. Try again later.",
                    "retryAfter", paymentVerifyRateLimiter.retryAfterSeconds(ip)));
        }

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

    @PostMapping(value = "/payment-callback", consumes = {
            MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.ALL_VALUE
    })
    public ResponseEntity<Void> paymentCallback(@RequestParam("razorpay_order_id") String razorpayOrderId,
                                                 @RequestParam("razorpay_payment_id") String razorpayPaymentId,
                                                 @RequestParam("razorpay_signature") String razorpaySignature) {

        boolean valid = razorpayService.verifyPaymentSignature(razorpayOrderId, razorpayPaymentId, razorpaySignature);

        if (valid) {
            Order order = orderService.markAsPaid(razorpayOrderId, razorpayPaymentId);
            log.info("Payment callback: Order {} marked PAID", order.getOrderNumber());
            return ResponseEntity.status(302).header("Location",
                    frontendUrl + "/order-confirmation/" + order.getOrderNumber()).build();
        } else {
            log.warn("Payment callback: Invalid signature for {}", razorpayOrderId);
            return ResponseEntity.status(302).header("Location",
                    frontendUrl + "/checkout?error=payment_failed").build();
        }
    }

    @GetMapping("/payment-callback")
    public ResponseEntity<Void> paymentCallbackFailed(
            @RequestParam(value = "razorpay_order_id", required = false) String razorpayOrderId) {
        log.warn("Payment callback: GET — user redirected after failed/cancelled payment. Razorpay order: {}",
                razorpayOrderId != null ? razorpayOrderId : "unknown");

        // DO NOT cancel orders here — unsigned GET can be spoofed.
        // Razorpay webhook (payment.failed) handles order cancellation with signature verification.
        // Expired orders are cleaned up by the scheduled job (30 min timeout).

        return ResponseEntity.status(302).header("Location",
                frontendUrl + "/checkout?error=payment_failed").build();
    }
}
