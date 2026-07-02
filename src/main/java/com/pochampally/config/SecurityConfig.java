package com.pochampally.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
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
    private final HttpCookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;
    // Present only when Google login is configured (see GoogleOAuthConfig).
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider;

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
                // API backend: unauthenticated access to a protected resource returns 403.
                // Without this, enabling oauth2Login would make Spring redirect (302) unauthenticated
                // API calls to the Google login page. The OAuth flow is still triggered explicitly
                // via /oauth2/authorization/google (the "Continue with Google" button).
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.FORBIDDEN)))
                .authorizeHttpRequests(auth -> auth
                        // Public: auth endpoints
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/logout",
                                "/api/auth/verify-email", "/api/auth/resend-verification",
                                "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()

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

                        // Public: Lambda compression callback (HMAC-authenticated, not JWT)
                        .requestMatchers(HttpMethod.POST, "/api/admin/videos/compression-done").permitAll()

                        // Public: CMS (banners, categories) + public settings
                        .requestMatchers(HttpMethod.GET, "/api/cms/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/settings/public").permitAll()

                        // Public: read policy pages
                        .requestMatchers(HttpMethod.GET, "/api/policies/**").permitAll()

                        // Public: health check
                        .requestMatchers("/actuator/health").permitAll()

                        // Public: Google OAuth2 login entry + callback (handled by Spring Security)
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()

                        // Checkout: payment callback is public (Razorpay redirect — POST for success, GET for failure)
                        .requestMatchers(HttpMethod.POST, "/api/checkout/payment-callback").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/checkout/payment-callback").permitAll()

                        // Checkout: requires login
                        .requestMatchers("/api/checkout/**").authenticated()

                        // Admin only
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        // Wire Google OAuth2 login only when configured (ClientRegistrationRepository bean present).
        // When Google login isn't set up, this is skipped and password login is unaffected.
        ClientRegistrationRepository clientRegistrationRepository = clientRegistrationRepositoryProvider.getIfAvailable();
        if (clientRegistrationRepository != null) {
            http.oauth2Login(oauth -> oauth
                    .clientRegistrationRepository(clientRegistrationRepository)
                    // Stateless: keep the in-flight auth request in a cookie, not the session
                    .authorizationEndpoint(endpoint -> endpoint
                            .authorizationRequestRepository(cookieAuthorizationRequestRepository))
                    .successHandler(oAuth2LoginSuccessHandler)
                    .failureHandler(oAuth2LoginFailureHandler));
        }

        return http.build();
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
