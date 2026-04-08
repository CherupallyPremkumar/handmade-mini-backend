package com.pochampally.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Sends emails via AWS SES v2 HTTP API (Signature V4).
 * No AWS SDK dependency needed — uses raw HTTP + HMAC signing.
 */
@Service
@Slf4j
public class EmailService {

    private final String accessKey;
    private final String secretKey;
    private final String region;
    private final String fromEmail;
    private final String frontendUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public EmailService(
            @Value("${aws.ses.access-key:}") String accessKey,
            @Value("${aws.ses.secret-key:}") String secretKey,
            @Value("${aws.ses.region:ap-south-1}") String region,
            @Value("${aws.ses.from-email:}") String fromEmail,
            @Value("${app.frontend-url:https://dhanunjaiah.com}") String frontendUrl,
            ObjectMapper objectMapper) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.region = region;
        this.fromEmail = fromEmail;
        this.frontendUrl = frontendUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    public boolean isConfigured() {
        return accessKey != null && !accessKey.isBlank()
                && secretKey != null && !secretKey.isBlank()
                && fromEmail != null && !fromEmail.isBlank();
    }

    // ═══ Order Emails ═══

    public void sendOrderConfirmationEmail(com.pochampally.entity.Order order) {
        if (!isConfigured() || order.getCustomerEmail() == null) return;
        try {
            String html = buildOrderConfirmationHtml(order);
            sendEmail(order.getCustomerEmail(), "Order Confirmed - " + order.getOrderNumber() + " | Dhanunjaiah Handlooms", html);
            log.info("Order confirmation email sent for {}", order.getOrderNumber());
        } catch (Exception e) {
            log.error("Failed to send order confirmation for {}: {}", order.getOrderNumber(), e.getMessage());
        }
    }

    public void sendShippingUpdateEmail(com.pochampally.entity.Order order) {
        if (!isConfigured() || order.getCustomerEmail() == null) return;
        try {
            String html = buildShippingUpdateHtml(order);
            sendEmail(order.getCustomerEmail(), "Your Order is Shipped! - " + order.getOrderNumber(), html);
            log.info("Shipping email sent for {}", order.getOrderNumber());
        } catch (Exception e) {
            log.error("Failed to send shipping email for {}: {}", order.getOrderNumber(), e.getMessage());
        }
    }

    public void sendDeliveryConfirmationEmail(com.pochampally.entity.Order order) {
        if (!isConfigured() || order.getCustomerEmail() == null) return;
        try {
            String html = buildDeliveryHtml(order);
            sendEmail(order.getCustomerEmail(), "Order Delivered - " + order.getOrderNumber(), html);
            log.info("Delivery email sent for {}", order.getOrderNumber());
        } catch (Exception e) {
            log.error("Failed to send delivery email for {}: {}", order.getOrderNumber(), e.getMessage());
        }
    }

    public void sendAdminOrderAlertEmail(String adminEmail, com.pochampally.entity.Order order) {
        if (!isConfigured() || adminEmail == null || adminEmail.isBlank()) return;
        try {
            String html = buildAdminAlertHtml(order);
            sendEmail(adminEmail, "New Order! " + order.getOrderNumber() + " - " + formatPaisa(order.getTotalAmount()), html);
            log.info("Admin alert sent for {}", order.getOrderNumber());
        } catch (Exception e) {
            log.error("Failed to send admin alert for {}: {}", order.getOrderNumber(), e.getMessage());
        }
    }

    // ═══ Abandoned Cart ═══

    public void sendAbandonedCartEmail(String toEmail, String userName, String itemsList) {
        if (!isConfigured() || toEmail == null) return;
        try {
            String name = userName != null ? escapeHtml(userName) : "there";
            String html = emailWrapper("You left something behind!",
                "<p style=\"color:#333;font-size:14px;\">Hi " + name + ",</p>" +
                "<p style=\"color:#666;font-size:14px;\">We noticed you have items waiting in your cart:</p>" +
                "<p style=\"color:#333;font-size:14px;font-weight:bold;margin:16px 0;\">" + escapeHtml(itemsList) + "</p>" +
                "<p style=\"color:#666;font-size:13px;\">Complete your purchase before they sell out!</p>" +
                "<div style=\"text-align:center;margin:24px 0;\">" +
                "<a href=\"" + frontendUrl + "/cart\" style=\"background:#5c4033;color:white;padding:14px 32px;text-decoration:none;border-radius:6px;font-size:16px;\">Complete Purchase</a></div>" +
                "<p style=\"color:#999;font-size:12px;\">If you've already completed your purchase, please ignore this email.</p>");
            sendEmail(toEmail, "You left items in your cart! - Dhanunjaiah Handlooms", html);
            log.info("Abandoned cart email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send abandoned cart email to {}: {}", toEmail, e.getMessage());
        }
    }

    // ═══ Verification Email ═══

    public void sendVerificationEmail(String toEmail, String userName, String token) {
        if (!isConfigured()) {
            log.warn("SES not configured — skipping verification email to {}", toEmail);
            return;
        }

        String verifyUrl = frontendUrl + "/verify-email?token=" + token;
        String subject = "Verify your email - Dhanunjaiah Handlooms";
        String htmlBody = buildVerificationHtml(userName, verifyUrl);

        try {
            sendEmail(toEmail, subject, htmlBody);
            log.info("Verification email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", toEmail, e.getMessage());
        }
    }

    private void sendEmail(String to, String subject, String htmlBody) throws Exception {
        String host = "email." + region + ".amazonaws.com";
        String endpoint = "https://" + host + "/v2/email/outbound-emails";

        // Build JSON payload safely via ObjectMapper — no string interpolation for user data
        String escapedHtml = objectMapper.writeValueAsString(htmlBody);
        String payload = objectMapper.writeValueAsString(Map.of(
                "FromEmailAddress", fromEmail,
                "Destination", Map.of("ToAddresses", new String[]{to}),
                "Content", Map.of("Simple", Map.of(
                        "Subject", Map.of("Data", subject, "Charset", "UTF-8"),
                        "Body", Map.of("Html", Map.of("Data", htmlBody, "Charset", "UTF-8"))
                ))
        ));

        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String amzDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
        String dateStamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        String contentHash = sha256Hex(payload);

        Map<String, String> headers = new TreeMap<>();
        headers.put("host", host);
        headers.put("x-amz-date", amzDate);
        headers.put("content-type", "application/json");

        String signedHeaders = String.join(";", headers.keySet());
        StringBuilder canonicalHeaders = new StringBuilder();
        headers.forEach((k, v) -> canonicalHeaders.append(k).append(":").append(v).append("\n"));

        String canonicalRequest = "POST\n/v2/email/outbound-emails\n\n"
                + canonicalHeaders + "\n" + signedHeaders + "\n" + contentHash;

        String credentialScope = dateStamp + "/" + region + "/ses/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + credentialScope + "\n"
                + sha256Hex(canonicalRequest);

        byte[] signingKey = getSignatureKey(secretKey, dateStamp, region, "ses");
        String signature = HexFormat.of().formatHex(hmacSha256(signingKey, stringToSign));

        String authHeader = "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("X-Amz-Date", amzDate)
                .header("Authorization", authHeader)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("SES send failed. Status: {}, Body: {}", response.statusCode(), response.body());
            throw new RuntimeException("SES email send failed: " + response.statusCode());
        }
    }

    private String buildVerificationHtml(String userName, String verifyUrl) {
        String name = escapeHtml(userName != null ? userName : "Customer");
        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"></head>
                <body style="font-family: Georgia, serif; background: #faf7f2; padding: 40px;">
                  <div style="max-width: 500px; margin: 0 auto; background: white; border-radius: 8px; padding: 40px; border: 1px solid #e8e0d4;">
                    <h1 style="color: #5c4033; font-size: 24px; margin-bottom: 8px;">Dhanunjaiah Handlooms</h1>
                    <p style="color: #8b7355; margin-bottom: 24px;">Handwoven Pochampally Ikat Sarees</p>
                    <hr style="border: none; border-top: 1px solid #e8e0d4; margin: 24px 0;">
                    <p style="color: #333; font-size: 16px;">Hello %s,</p>
                    <p style="color: #333; font-size: 16px;">Please verify your email address to complete your registration.</p>
                    <div style="text-align: center; margin: 32px 0;">
                      <a href="%s" style="background: #5c4033; color: white; padding: 14px 32px; text-decoration: none; border-radius: 6px; font-size: 16px;">Verify Email</a>
                    </div>
                    <p style="color: #999; font-size: 13px;">This link expires in 24 hours.</p>
                    <p style="color: #999; font-size: 13px;">If you didn't create an account, you can safely ignore this email.</p>
                  </div>
                </body>
                </html>
                """.formatted(name, verifyUrl);
    }

    private static String sha256Hex(String data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private static byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] getSignatureKey(String key, String dateStamp, String region, String service) throws Exception {
        byte[] kSecret = ("AWS4" + key).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSha256(kSecret, dateStamp);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, service);
        return hmacSha256(kService, "aws4_request");
    }

    private static String escapeHtml(String input) {
        return input.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String formatPaisa(long paisa) {
        return "₹" + String.format("%,.2f", paisa / 100.0);
    }

    private String emailWrapper(String title, String content) {
        return """
            <!DOCTYPE html><html><head><meta charset="UTF-8"></head>
            <body style="font-family:Georgia,serif;background:#faf7f2;padding:40px 0;">
            <div style="max-width:560px;margin:0 auto;background:white;border-radius:8px;border:1px solid #e8e0d4;overflow:hidden;">
              <div style="background:#5c4033;padding:20px 30px;">
                <h1 style="color:#d4a017;font-size:20px;margin:0;">Dhanunjaiah Handlooms</h1>
                <p style="color:#ddd;font-size:12px;margin:4px 0 0;">Handwoven Pochampally Ikat Sarees</p>
              </div>
              <div style="padding:30px;">
                <h2 style="color:#5c4033;font-size:18px;margin:0 0 16px;">%s</h2>
                %s
              </div>
              <div style="background:#faf7f2;padding:16px 30px;border-top:1px solid #e8e0d4;">
                <p style="color:#999;font-size:11px;margin:0;">Questions? Reply to this email or call us.</p>
                <p style="color:#999;font-size:11px;margin:4px 0 0;">© Dhanunjaiah Handlooms, Pochampally, Telangana</p>
              </div>
            </div></body></html>
            """.formatted(escapeHtml(title), content);
    }

    private String buildItemsTable(com.pochampally.entity.Order order) {
        StringBuilder rows = new StringBuilder();
        for (var item : order.getItems()) {
            rows.append("<tr><td style=\"padding:8px;border-bottom:1px solid #eee;\">")
                .append(escapeHtml(item.getProductName()))
                .append("</td><td style=\"padding:8px;border-bottom:1px solid #eee;text-align:center;\">")
                .append(item.getQuantity())
                .append("</td><td style=\"padding:8px;border-bottom:1px solid #eee;text-align:right;\">")
                .append(formatPaisa(item.getTotalPrice()))
                .append("</td></tr>");
        }
        return """
            <table style="width:100%%;border-collapse:collapse;margin:16px 0;">
              <tr style="background:#faf7f2;"><th style="padding:8px;text-align:left;font-size:13px;">Item</th>
              <th style="padding:8px;text-align:center;font-size:13px;">Qty</th>
              <th style="padding:8px;text-align:right;font-size:13px;">Amount</th></tr>
              %s
              <tr><td colspan="2" style="padding:8px;text-align:right;font-size:13px;">Subtotal</td>
              <td style="padding:8px;text-align:right;font-size:13px;">%s</td></tr>
              <tr><td colspan="2" style="padding:8px;text-align:right;font-size:13px;">GST</td>
              <td style="padding:8px;text-align:right;font-size:13px;">%s</td></tr>
              <tr><td colspan="2" style="padding:8px;text-align:right;font-size:13px;">Shipping</td>
              <td style="padding:8px;text-align:right;font-size:13px;">%s</td></tr>
              <tr style="background:#5c4033;color:white;"><td colspan="2" style="padding:10px;text-align:right;font-weight:bold;">Total</td>
              <td style="padding:10px;text-align:right;font-weight:bold;">%s</td></tr>
            </table>
            """.formatted(rows, formatPaisa(order.getSubtotal()), formatPaisa(order.getGstAmount()),
                order.getShippingCost() == 0 ? "FREE" : formatPaisa(order.getShippingCost()),
                formatPaisa(order.getTotalAmount()));
    }

    private String buildOrderConfirmationHtml(com.pochampally.entity.Order order) {
        var addr = order.getShippingAddress();
        String address = addr != null ? escapeHtml(
                addr.getOrDefault("line1", "") + ", " +
                addr.getOrDefault("city", "") + ", " +
                addr.getOrDefault("state", "") + " - " +
                addr.getOrDefault("pincode", "")) : "";

        return emailWrapper("Order Confirmed!",
            "<p style=\"color:#333;font-size:14px;\">Thank you for your order, " + escapeHtml(order.getCustomerName()) + "!</p>" +
            "<p style=\"color:#666;font-size:13px;\">Order Number: <strong>" + order.getOrderNumber() + "</strong></p>" +
            buildItemsTable(order) +
            "<p style=\"color:#666;font-size:13px;\">Delivering to: " + address + "</p>" +
            "<div style=\"text-align:center;margin:24px 0;\">" +
            "<a href=\"" + frontendUrl + "/track?order=" + order.getOrderNumber() + "\" style=\"background:#5c4033;color:white;padding:12px 28px;text-decoration:none;border-radius:6px;font-size:14px;\">Track Order</a></div>");
    }

    private String buildShippingUpdateHtml(com.pochampally.entity.Order order) {
        String tracking = order.getTrackingNumber() != null ? order.getTrackingNumber() : "Will be updated soon";
        return emailWrapper("Your Order is on the Way!",
            "<p style=\"color:#333;font-size:14px;\">Great news! Your order <strong>" + order.getOrderNumber() + "</strong> has been shipped.</p>" +
            "<p style=\"color:#666;font-size:13px;\">Tracking Number: <strong>" + escapeHtml(tracking) + "</strong></p>" +
            "<p style=\"color:#666;font-size:13px;\">Items: " + order.getItems().size() + " item(s)</p>" +
            "<div style=\"text-align:center;margin:24px 0;\">" +
            "<a href=\"" + frontendUrl + "/track?order=" + order.getOrderNumber() + "\" style=\"background:#5c4033;color:white;padding:12px 28px;text-decoration:none;border-radius:6px;font-size:14px;\">Track Order</a></div>");
    }

    private String buildDeliveryHtml(com.pochampally.entity.Order order) {
        return emailWrapper("Order Delivered!",
            "<p style=\"color:#333;font-size:14px;\">Your order <strong>" + order.getOrderNumber() + "</strong> has been delivered.</p>" +
            "<p style=\"color:#666;font-size:13px;\">We hope you love your new saree! If you have any issues, please don't hesitate to contact us.</p>" +
            "<div style=\"text-align:center;margin:24px 0;\">" +
            "<a href=\"" + frontendUrl + "/sarees\" style=\"background:#5c4033;color:white;padding:12px 28px;text-decoration:none;border-radius:6px;font-size:14px;\">Shop More</a></div>");
    }

    private String buildAdminAlertHtml(com.pochampally.entity.Order order) {
        return emailWrapper("New Order Received!",
            "<p style=\"color:#333;font-size:14px;\">A new order has been placed.</p>" +
            "<p style=\"color:#666;font-size:13px;\">Order: <strong>" + order.getOrderNumber() + "</strong></p>" +
            "<p style=\"color:#666;font-size:13px;\">Customer: " + escapeHtml(order.getCustomerName()) + " (" + escapeHtml(order.getCustomerPhone()) + ")</p>" +
            "<p style=\"color:#666;font-size:13px;\">Amount: <strong>" + formatPaisa(order.getTotalAmount()) + "</strong></p>" +
            buildItemsTable(order));
    }
}
