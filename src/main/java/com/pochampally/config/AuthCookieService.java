package com.pochampally.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for the auth (JWT) cookie.
 * Used by both password login (AuthController) and Google OAuth login
 * (OAuth2LoginSuccessHandler) so the cookie attributes stay identical.
 */
@Component
public class AuthCookieService {

    public static final String AUTH_COOKIE = "dhn_token";
    private static final int MAX_AGE_SECONDS = 24 * 60 * 60; // 24 hours — matches JWT expiry

    /** Set the auth cookie: Secure, HttpOnly, SameSite=None (cross-site frontend ↔ API). */
    public void set(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(AUTH_COOKIE, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(MAX_AGE_SECONDS);
        cookie.setAttribute("SameSite", "None");
        response.addCookie(cookie);
    }

    /** Clear the auth cookie (logout). */
    public void clear(HttpServletResponse response) {
        Cookie cookie = new Cookie(AUTH_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "None");
        response.addCookie(cookie);
    }
}
