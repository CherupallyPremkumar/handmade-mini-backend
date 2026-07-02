package com.pochampally.config;

import com.pochampally.entity.User;
import com.pochampally.service.AuthService;
import com.pochampally.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * On successful Google login: find-or-create the local user (link by email),
 * issue the standard dhn_token cookie, and redirect back to the frontend.
 * Mirrors what AuthController does for password login, so the rest of the app
 * (JwtAuthFilter, /api/auth/me) is unchanged.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AuthService authService;
    private final JwtService jwtService;
    private final AuthCookieService authCookieService;

    @Value("${app.frontend-url:https://dhanunjaiah.com}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String googleSub = oAuth2User.getAttribute("sub");

        try {
            User user = authService.oauthFindOrCreate(email, name, googleSub);
            String token = jwtService.generateToken(user);
            authCookieService.set(response, token);

            log.info("Google login success for {} (role {})", user.getEmail(), user.getRole());
            getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/oauth-callback");
        } catch (Exception e) {
            log.warn("Google login post-processing failed for {}: {}", email, e.getMessage());
            getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/login?error=oauth_failed");
        }
    }
}
