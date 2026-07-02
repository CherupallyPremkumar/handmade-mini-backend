package com.pochampally.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.beans.factory.annotation.Value;

/**
 * Registers the Google OAuth2 client ONLY when GOOGLE_CLIENT_ID is configured.
 *
 * Without this guard, an empty client-id would make Spring fail to build the
 * ClientRegistration at startup (clientId must not be empty) — breaking app boot
 * and the test context in every environment where Google login isn't set up.
 *
 * When this bean is absent, SecurityConfig skips the oauth2Login wiring entirely,
 * so password login keeps working unchanged.
 */
@Configuration
@ConditionalOnExpression("'${google.oauth.client-id:}' != ''")
public class GoogleOAuthConfig {

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${google.oauth.client-id}") String clientId,
            @Value("${google.oauth.client-secret}") String clientSecret) {

        ClientRegistration google = CommonOAuth2Provider.GOOGLE
                .getBuilder("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .redirectUri("{baseUrl}/login/oauth2/code/google")
                .scope("openid", "email", "profile")
                .build();

        return new InMemoryClientRegistrationRepository(google);
    }
}
