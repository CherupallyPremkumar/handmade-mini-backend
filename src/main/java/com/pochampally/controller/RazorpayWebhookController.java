package com.pochampally.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pochampally.service.OrderService;
import com.pochampally.service.RazorpayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class RazorpayWebhookController {

    private final RazorpayService razorpayService;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    /**
     * Razorpay webhook endpoint.
     * Razorpay sends POST with:
     * - Header: X-Razorpay-Signature (HMAC-SHA256 of body using webhook secret)
     * - Body: JSON with event type and payment details
     *
     * Events handled:
     * - payment.captured: Payment successfully captured
     * - payment.failed: Payment failed
     */
    @PostMapping("/razorpay")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        // Verify webhook signature
        if (signature == null || !razorpayService.verifyWebhookSignature(rawBody, signature)) {
            log.warn("Razorpay webhook signature verification failed");
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        try {
            JsonNode payload = objectMapper.readTree(rawBody);
            String event = payload.get("event").asText();

            log.info("Razorpay webhook received. Event: {}", event);

            switch (event) {
                case "payment.captured" -> handlePaymentCaptured(payload);
                case "payment.failed" -> handlePaymentFailed(payload);
                default -> log.info("Ignoring unhandled Razorpay webhook event: {}", event);
            }

            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            log.error("Error processing Razorpay webhook", e);
            // Return 200 to avoid Razorpay retries for parsing errors
            return ResponseEntity.ok("OK");
        }
    }

    private void handlePaymentCaptured(JsonNode payload) {
        try {
            JsonNode paymentEntity = payload.at("/payload/payment/entity");
            String razorpayOrderId = paymentEntity.get("order_id").asText();
            String razorpayPaymentId = paymentEntity.get("id").asText();

            log.info("Payment captured via webhook. Razorpay order: {}, payment: {}",
                    razorpayOrderId, razorpayPaymentId);

            orderService.markAsPaid(razorpayOrderId, razorpayPaymentId);

        } catch (Exception e) {
            log.error("Error handling payment.captured webhook", e);
        }
    }

    private void handlePaymentFailed(JsonNode payload) {
        try {
            JsonNode paymentEntity = payload.at("/payload/payment/entity");
            String razorpayOrderId = paymentEntity.get("order_id").asText();

            log.warn("Payment failed via webhook. Razorpay order: {}", razorpayOrderId);

            orderService.markPaymentFailed(razorpayOrderId);

        } catch (Exception e) {
            log.error("Error handling payment.failed webhook", e);
        }
    }
}
