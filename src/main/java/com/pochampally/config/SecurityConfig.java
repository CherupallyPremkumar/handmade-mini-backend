package com.pochampally.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public: auth endpoints
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/logout").permitAll()

                        // Public: browse products
                        .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()

                        // Public: cart (session-based, no auth needed)
                        .requestMatchers("/api/cart/**").permitAll()

                        // Authenticated: my orders
                        .requestMatchers(HttpMethod.GET, "/api/orders/my/**").authenticated()

                        // Public: track order by order number
                        .requestMatchers(HttpMethod.GET, "/api/orders/track/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/orders/{orderNumber}").permitAll()

                        // Public: payment webhooks
                        .requestMatchers("/api/webhooks/**").permitAll()

                        // Public: health check
                        .requestMatchers("/actuator/health").permitAll()

                        // Checkout: payment callback is public (Razorpay redirect)
                        .requestMatchers(HttpMethod.POST, "/api/checkout/payment-callback").permitAll()

                        // Checkout: requires login
                        .requestMatchers("/api/checkout/**").authenticated()

                        // Admin only
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @org.springframework.beans.factory.annotation.Value("${app.cors-origins}")
    private String corsOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(corsOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        // Disable CORS for payment callback + webhooks (server-to-server, not browser AJAX)
        CorsConfiguration noCors = new CorsConfiguration();
        noCors.addAllowedOriginPattern("*");
        noCors.setAllowedMethods(List.of("POST", "GET", "OPTIONS"));
        noCors.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/checkout/payment-callback", noCors);
        source.registerCorsConfiguration("/api/webhooks/**", noCors);
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
