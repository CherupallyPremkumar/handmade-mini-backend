package com.pochampally.service;

import com.pochampally.dto.AuthResponse;
import com.pochampally.dto.LoginRequest;
import com.pochampally.dto.RegisterRequest;
import com.pochampally.entity.User;
import com.pochampally.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_EXPIRY_HOURS = 24;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalStateException("Email already registered: " + request.getEmail());
        }

        String verificationToken = generateVerificationToken();

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(User.Role.CUSTOMER)
                .isActive(true)
                .emailVerified(false)
                .verificationToken(verificationToken)
                .verificationTokenExpiry(Instant.now().plus(TOKEN_EXPIRY_HOURS, ChronoUnit.HOURS))
                .build();

        user = userRepository.save(user);

        // Send verification email (async-safe — won't fail registration if email fails)
        emailService.sendVerificationEmail(user.getEmail(), user.getName(), verificationToken);

        String token = jwtService.generateToken(user);

        return AuthResponse.builder()
                .token(token)
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .emailVerified(false)
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AuthenticationException("Invalid email or password"));

        if (!user.getIsActive()) {
            throw new AuthenticationException("Account is deactivated");
        }

        // OAuth-only account has no local password — don't attempt a bcrypt match against null.
        if (user.getPasswordHash() == null) {
            throw new AuthenticationException("This account uses Google sign-in. Please continue with Google.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AuthenticationException("Invalid email or password");
        }

        String token = jwtService.generateToken(user);

        return AuthResponse.builder()
                .token(token)
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .emailVerified(user.getEmailVerified())
                .build();
    }

    @Transactional
    public String verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification token"));

        if (user.getEmailVerified()) {
            return "Email already verified";
        }

        if (user.getVerificationTokenExpiry() != null && user.getVerificationTokenExpiry().isBefore(Instant.now())) {
            throw new IllegalStateException("Verification link has expired. Please request a new one.");
        }

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        userRepository.save(user);

        log.info("Email verified for user: {}", user.getEmail());
        return "Email verified successfully";
    }

    @Transactional
    public void resendVerification(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getEmailVerified()) {
            throw new IllegalStateException("Email is already verified");
        }

        String newToken = generateVerificationToken();
        user.setVerificationToken(newToken);
        user.setVerificationTokenExpiry(Instant.now().plus(TOKEN_EXPIRY_HOURS, ChronoUnit.HOURS));
        userRepository.save(user);

        emailService.sendVerificationEmail(user.getEmail(), user.getName(), newToken);
        log.info("Verification email resent to: {}", user.getEmail());
    }

    @Transactional
    public void requestPasswordReset(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("No account found with this email"));

        String resetToken = generateVerificationToken();
        user.setVerificationToken(resetToken);
        user.setVerificationTokenExpiry(Instant.now().plus(1, ChronoUnit.HOURS));
        userRepository.save(user);

        emailService.sendPasswordResetEmail(user.getEmail(), user.getName(), resetToken);
        log.info("Password reset requested for: {}", user.getEmail());
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token"));

        if (user.getVerificationTokenExpiry() != null && user.getVerificationTokenExpiry().isBefore(Instant.now())) {
            throw new IllegalStateException("Reset link has expired. Please request a new one.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        userRepository.save(user);
        log.info("Password reset successful for: {}", user.getEmail());
    }

    public User getUserById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    /**
     * Find-or-create a user from a verified Google identity.
     * Linking policy: match on email. Google has already verified email ownership,
     * so linking an existing local account to Google is safe (no takeover risk).
     * Returns the persisted user; the caller issues the JWT.
     */
    @Transactional
    public User oauthFindOrCreate(String email, String name, String googleSub) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Google account did not provide an email");
        }

        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            user = User.builder()
                    .name(name != null ? name : email.split("@")[0])
                    .email(email)
                    .passwordHash(null)                       // OAuth-only account
                    .authProvider(User.AuthProvider.GOOGLE)
                    .googleSub(googleSub)
                    .role(User.Role.CUSTOMER)
                    .isActive(true)
                    .emailVerified(true)                      // Google-verified email
                    .build();
            user = userRepository.save(user);
            log.info("Created new user via Google sign-in: {}", email);
            return user;
        }

        if (!user.getIsActive()) {
            throw new AuthenticationException("Account is deactivated");
        }

        // Link an existing (local) account to Google on first Google sign-in.
        boolean changed = false;
        if (user.getGoogleSub() == null && googleSub != null) {
            user.setGoogleSub(googleSub);
            changed = true;
        }
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            user.setEmailVerified(true);                      // Google verified it
            changed = true;
        }
        if (changed) {
            userRepository.save(user);
            log.info("Linked existing account to Google sign-in: {}", email);
        }
        return user;
    }

    private String generateVerificationToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return java.util.HexFormat.of().formatHex(bytes);
    }

    /**
     * Custom authentication exception that maps to HTTP 401.
     */
    public static class AuthenticationException extends RuntimeException {
        public AuthenticationException(String message) {
            super(message);
        }
    }
}
