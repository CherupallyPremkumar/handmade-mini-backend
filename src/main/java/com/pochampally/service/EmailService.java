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
}
