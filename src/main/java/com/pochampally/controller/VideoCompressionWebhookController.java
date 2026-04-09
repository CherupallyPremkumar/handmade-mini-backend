package com.pochampally.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pochampally.entity.Product;
import com.pochampally.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

/**
 * Receives callbacks from the dhanunjaiah-video-compressor Lambda when
 * video compression completes (or fails). Authenticated via HMAC-SHA256
 * shared secret in the X-Webhook-Signature header.
 *
 * Idempotent: duplicate READY callbacks for the same product are ignored.
 */
@RestController
@RequestMapping("/api/admin/videos")
@RequiredArgsConstructor
@Slf4j
public class VideoCompressionWebhookController {

    private final ProductService productService;
    private final ObjectMapper objectMapper;

    @Value("${aws.lambda.webhook-secret:}")
    private String webhookSecret;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    /**
     * Called by the Lambda after it finishes compressing a video.
     *
     * Request body:
     * {
     *   "productId": "uuid",
     *   "status": "READY" or "FAILED",
     *   "videoUrl": "https://...",       // if READY
     *   "originalSizeBytes": 523456000,  // if READY
     *   "compressedSizeBytes": 23456000, // if READY
     *   "error": "string"                // if FAILED
     * }
     *
     * Header:
     *   X-Webhook-Signature: hex-encoded HMAC-SHA256 of raw body
     */
    @PostMapping(value = "/compression-done",
            consumes = "application/json",
            produces = "application/json")
    public ResponseEntity<Map<String, Object>> compressionDone(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature) {

        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("Webhook called but LAMBDA_WEBHOOK_SECRET not configured — rejecting");
            return ResponseEntity.status(503).body(Map.of("error", "Webhook not configured"));
        }

        if (signature == null || signature.isBlank()) {
            log.warn("Webhook called without signature header");
            return ResponseEntity.status(401).body(Map.of("error", "Missing signature"));
        }

        if (!verifySignature(rawBody, signature)) {
            log.warn("Webhook signature verification failed");
            return ResponseEntity.status(401).body(Map.of("error", "Invalid signature"));
        }

        // Parse JSON after signature verification
        Map<String, Object> body;
        try {
            body = objectMapper.readValue(rawBody, MAP_TYPE);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid JSON"));
        }

        String productId = (String) body.get("productId");
        String status = (String) body.get("status");

        if (productId == null || status == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "productId and status required"));
        }

        // Idempotency check — prevent duplicate Lambda invocations from overwriting state
        Product existing;
        try {
            existing = productService.getById(productId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", "Product not found"));
        }

        if (existing.getVideoStatus() == Product.VideoStatus.READY) {
            log.info("Product {} already READY — ignoring duplicate compression webhook", productId);
            return ResponseEntity.ok(Map.of("ok", true, "duplicate", true));
        }

        if ("READY".equals(status)) {
            String videoUrl = (String) body.get("videoUrl");
            if (videoUrl == null || videoUrl.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "videoUrl required when status=READY"));
            }
            productService.updateVideoStatus(productId, videoUrl, Product.VideoStatus.READY);

            Object origSize = body.get("originalSizeBytes");
            Object compSize = body.get("compressedSizeBytes");
            if (origSize instanceof Number && compSize instanceof Number) {
                double orig = ((Number) origSize).doubleValue();
                double comp = ((Number) compSize).doubleValue();
                double reduction = (1 - comp / orig) * 100;
                log.info("Video compression READY for product {}: {} → {} ({}% reduction)",
                        productId,
                        formatMb(orig),
                        formatMb(comp),
                        String.format("%.1f", reduction));
            } else {
                log.info("Video compression READY for product {}: {}", productId, videoUrl);
            }

        } else if ("FAILED".equals(status)) {
            String error = (String) body.get("error");
            // Keep whatever URL was there (probably still the temp one) and flag as failed
            productService.updateVideoStatus(productId, existing.getVideoUrl(), Product.VideoStatus.FAILED);
            log.error("Video compression FAILED for product {}: {}", productId, error);

        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "status must be READY or FAILED"));
        }

        return ResponseEntity.ok(Map.of("ok", true));
    }

    /**
     * Verify HMAC-SHA256 signature of the raw body.
     * Constant-time comparison to prevent timing attacks.
     */
    private boolean verifySignature(String rawBody, String providedSignature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            byte[] computed = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String expected = HexFormat.of().formatHex(computed);
            return constantTimeEquals(expected, providedSignature);
        } catch (Exception e) {
            log.error("Signature verification error: {}", e.getMessage());
            return false;
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private String formatMb(double bytes) {
        return String.format("%.1f MB", bytes / 1024 / 1024);
    }
}
