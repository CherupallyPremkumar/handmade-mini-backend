package com.pochampally.controller;

import com.pochampally.config.LoginRateLimiter;
import com.pochampally.dto.AuthResponse;
import com.pochampally.dto.LoginRequest;
import com.pochampally.dto.RegisterRequest;
import com.pochampally.entity.User;
import com.pochampally.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimiter loginRateLimiter;

    private static final String AUTH_COOKIE = "dhn_token";
    private static final int COOKIE_MAX_AGE = 24 * 60 * 60; // 24 hours

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request,
                                                         HttpServletResponse response) {
        AuthResponse authResponse = authService.register(request);
        setAuthCookie(response, authResponse.getToken());

        // Return user info + token (for API clients) AND set httpOnly cookie (for browser)
        return ResponseEntity.ok(Map.of(
                "token", authResponse.getToken(),
                "name", authResponse.getName(),
                "email", authResponse.getEmail(),
                "role", authResponse.getRole()
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                    HttpServletRequest httpRequest,
                                    HttpServletResponse response) {
        String clientIp = resolveClientIp(httpRequest);
        if (!loginRateLimiter.tryAcquire(clientIp)) {
            long retryAfter = loginRateLimiter.retryAfterSeconds(clientIp);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(retryAfter))
                    .body(Map.of(
                            "error", "Too many login attempts. Try again in " + retryAfter + " seconds.",
                            "retryAfterSeconds", retryAfter
                    ));
        }

        AuthResponse authResponse = authService.login(request);
        setAuthCookie(response, authResponse.getToken());

        return ResponseEntity.ok(Map.of(
                "token", authResponse.getToken(),
                "name", authResponse.getName(),
                "email", authResponse.getEmail(),
                "role", authResponse.getRole()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie(AUTH_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "None");
        response.addCookie(cookie);

        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        String userId = authentication.getName();
        User user = authService.getUserById(userId);

        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "name", user.getName() != null ? user.getName() : "",
                "email", user.getEmail(),
                "phone", user.getPhone() != null ? user.getPhone() : "",
                "role", user.getRole().name()
        ));
    }

    private void setAuthCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(AUTH_COOKIE, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(COOKIE_MAX_AGE);
        cookie.setAttribute("SameSite", "None");
        response.addCookie(cookie);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
