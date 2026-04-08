package com.pochampally.service.email;

/**
 * Strategy interface for email providers.
 * Implementations: SES, Resend. Switch via EMAIL_PROVIDER env var.
 */
public interface EmailProvider {

    boolean isConfigured();

    void send(String to, String subject, String htmlBody) throws Exception;
}
