package com.pochampally.service.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * AWS SES v2 email provider. Uses raw HTTP + Signature V4.
 * Requires SES production access (or verified emails in sandbox).
 */
@Slf4j
public class SesProvider implements EmailProvider {

    private final String accessKey;
    private final String secretKey;
    private final String region;
    private final String fromEmail;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public SesProvider(String accessKey, String secretKey, String region, String fromEmail, ObjectMapper objectMapper) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.region = region;
        this.fromEmail = fromEmail;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public boolean isConfigured() {
        return accessKey != null && !accessKey.isBlank()
                && secretKey != null && !secretKey.isBlank()
                && fromEmail != null && !fromEmail.isBlank();
    }

    @Override
    public void send(String to, String subject, String htmlBody) throws Exception {
        String host = "email." + region + ".amazonaws.com";
        String endpoint = "https://" + host + "/v2/email/outbound-emails";

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

        log.debug("SES email sent to {}", to);
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
}
