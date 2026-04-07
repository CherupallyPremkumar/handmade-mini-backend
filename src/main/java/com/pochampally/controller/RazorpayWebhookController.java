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
            JsonNode eventNode = payload.get("event");
            if (eventNode == null || eventNode.isNull()) {
                log.warn("Razorpay webhook missing 'event' field");
                return ResponseEntity.badRequest().body("Missing event field");
            }
            String event = eventNode.asText();

            log.info("Razorpay webhook received. Event: {}", event);

            switch (event) {
                case "payment.captured" -> handlePaymentCaptured(payload);
                case "payment.failed" -> handlePaymentFailed(payload);
                default -> log.info("Ignoring unhandled Razorpay webhook event: {}", event);
            }

            return ResponseEntity.ok("OK");

        } catch (IllegalArgumentException | IllegalStateException |
                 com.fasterxml.jackson.core.JsonProcessingException e) {
            // Non-retryable: bad data, invalid state, malformed JSON — return 200 so Razorpay doesn't retry
            log.warn("Webhook processing rejected: {}", e.getMessage());
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            // Retryable: DB down, network error — return 500 so Razorpay retries
            log.error("Error processing Razorpay webhook", e);
            return ResponseEntity.internalServerError().body("Processing error");
        }
    }

    private void handlePaymentCaptured(JsonNode payload) {
        JsonNode paymentEntity = payload.at("/payload/payment/entity");
        String razorpayOrderId = extractField(paymentEntity, "order_id");
        String razorpayPaymentId = extractField(paymentEntity, "id");

        if (razorpayOrderId == null || razorpayPaymentId == null) {
            throw new IllegalArgumentException("Webhook payload missing order_id or payment id");
        }

        log.info("Payment captured via webhook. Razorpay order: {}, payment: {}",
                razorpayOrderId, razorpayPaymentId);

        orderService.markAsPaid(razorpayOrderId, razorpayPaymentId);
    }

    private void handlePaymentFailed(JsonNode payload) {
        JsonNode paymentEntity = payload.at("/payload/payment/entity");
        String razorpayOrderId = extractField(paymentEntity, "order_id");

        if (razorpayOrderId == null) {
            throw new IllegalArgumentException("Webhook payload missing order_id");
        }

        log.warn("Payment failed via webhook. Razorpay order: {}", razorpayOrderId);

        orderService.markPaymentFailed(razorpayOrderId);
    }

    private String extractField(JsonNode node, String field) {
        if (node == null || node.isMissingNode()) return null;
        JsonNode fieldNode = node.get(field);
        if (fieldNode == null || fieldNode.isNull() || fieldNode.asText().isBlank()) return null;
        return fieldNode.asText();
    }
}
