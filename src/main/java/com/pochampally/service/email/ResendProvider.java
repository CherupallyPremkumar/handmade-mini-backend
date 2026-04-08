package com.pochampally.service.email;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Resend.com email provider. Simple HTTP API, no SDK needed.
 * Free tier: 3,000 emails/month.
 */
@Slf4j
public class ResendProvider implements EmailProvider {

    private final String apiKey;
    private final String fromEmail;
    private final HttpClient httpClient;

    private static final String RESEND_API = "https://api.resend.com/emails";

    public ResendProvider(String apiKey, String fromEmail) {
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && fromEmail != null && !fromEmail.isBlank();
    }

    @Override
    public void send(String to, String subject, String htmlBody) throws Exception {
        String json = """
                {"from":"%s","to":["%s"],"subject":"%s","html":%s}
                """.formatted(fromEmail, to, escapeJson(subject),
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(htmlBody));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(RESEND_API))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("Resend send failed. Status: {}, Body: {}", response.statusCode(), response.body());
            throw new RuntimeException("Resend email send failed: " + response.statusCode());
        }

        log.debug("Resend email sent to {}", to);
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
