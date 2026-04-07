package com.pochampally.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@Slf4j
public class RazorpayService {

    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private static final String RAZORPAY_API_BASE = "https://api.razorpay.com/v1";

    public RazorpayService(
            @Value("${razorpay.key-id}") String keyId,
            @Value("${razorpay.key-secret}") String keySecret,
            @Value("${razorpay.webhook-secret}") String webhookSecret,
            ObjectMapper objectMapper) {
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.webhookSecret = webhookSecret;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a Razorpay order via their API.
     * Amount is in paisa (smallest currency unit).
     *
     * @param amountInPaisa total amount in paisa
     * @param receipt       unique receipt identifier (our order number)
     * @return Razorpay order ID
     */
    public String createRazorpayOrder(long amountInPaisa, String receipt) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "amount", amountInPaisa,
                    "currency", "INR",
                    "receipt", receipt,
                    "payment_capture", 1
            );

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            String auth = Base64.getEncoder().encodeToString(
                    (keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RAZORPAY_API_BASE + "/orders"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Basic " + auth)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Razorpay order creation failed. Status: {}, Body: {}", response.statusCode(), response.body());
                throw new RuntimeException("Failed to create Razorpay order. Status: " + response.statusCode());
            }

            JsonNode responseJson = objectMapper.readTree(response.body());
            String razorpayOrderId = responseJson.get("id").asText();
            log.info("Created Razorpay order: {} for receipt: {}", razorpayOrderId, receipt);
            return razorpayOrderId;

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error creating Razorpay order for receipt: {}", receipt, e);
            throw new RuntimeException("Failed to create Razorpay order", e);
        }
    }

    /**
     * Verifies the payment signature returned by Razorpay checkout.
     * The expected signature is HMAC-SHA256 of "razorpayOrderId|razorpayPaymentId" using the key secret.
     *
     * @param razorpayOrderId   the Razorpay order ID
     * @param razorpayPaymentId the Razorpay payment ID
     * @param razorpaySignature the signature from Razorpay
     * @return true if signature is valid
     */
    public boolean verifyPaymentSignature(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) {
        try {
            String payload = razorpayOrderId + "|" + razorpayPaymentId;
            String expectedSignature = hmacSha256(payload, keySecret);

            boolean valid = constantTimeEquals(expectedSignature, razorpaySignature);
            if (!valid) {
                log.warn("Payment signature verification failed for order: {}, payment: {}",
                        razorpayOrderId, razorpayPaymentId);
            }
            return valid;

        } catch (Exception e) {
            log.error("Error verifying payment signature", e);
            return false;
        }
    }

    /**
     * Verifies the webhook signature from Razorpay.
     * The expected signature is HMAC-SHA256 of the raw request body using the webhook secret.
     *
     * @param requestBody      the raw webhook request body
     * @param receivedSignature the X-Razorpay-Signature header value
     * @return true if webhook signature is valid
     */
    public boolean verifyWebhookSignature(String requestBody, String receivedSignature) {
        try {
            String expectedSignature = hmacSha256(requestBody, webhookSecret);
            boolean valid = constantTimeEquals(expectedSignature, receivedSignature);
            if (!valid) {
                log.warn("Webhook signature verification failed");
            }
            return valid;

        } catch (Exception e) {
            log.error("Error verifying webhook signature", e);
            return false;
        }
    }

    public String getKeyId() {
        return keyId;
    }

    private String hmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        if (aBytes.length != bBytes.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        return result == 0;
    }
}
