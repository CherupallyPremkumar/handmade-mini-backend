package com.pochampally.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** On Google login failure (denied consent, error, etc.), redirect to the frontend login page. */
@Component
@Slf4j
public class OAuth2LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Value("${app.frontend-url:https://dhanunjaiah.com}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        log.warn("Google login failed: {}", exception.getMessage());
        getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/login?error=oauth_failed");
    }
}
