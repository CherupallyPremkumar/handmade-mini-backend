package com.pochampally.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * PasswordEncoder lives here (not in SecurityConfig) to avoid a bean cycle:
 * SecurityConfig depends on the OAuth2 login handlers, which depend on AuthService,
 * which depends on PasswordEncoder. Keeping the encoder in a separate config breaks
 * the SecurityConfig ↔ AuthService loop.
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
