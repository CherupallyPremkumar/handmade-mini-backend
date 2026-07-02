package com.pochampally.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "users", schema = "homebase_db")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(length = 200)
    private String name;

    @Column(nullable = false, unique = true, length = 200)
    private String email;

    @Column(length = 20)
    private String phone;

    // Nullable: OAuth-only accounts (e.g. Google sign-in) have no local password.
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "password_hash", length = 500)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    @Builder.Default
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "google_sub", unique = true, length = 255)
    private String googleSub;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.CUSTOMER;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(name = "verification_token", length = 100)
    private String verificationToken;

    @Column(name = "verification_token_expiry")
    private Instant verificationTokenExpiry;

    @Column(name = "last_cart_reminder_sent")
    private Instant lastCartReminderSent;

    @Column(name = "created_time", nullable = false, updatable = false)
    private Instant createdTime;

    @PrePersist
    protected void onCreate() {
        if (createdTime == null) {
            createdTime = Instant.now();
        }
    }

    public enum Role {
        ADMIN, CUSTOMER
    }

    public enum AuthProvider {
        LOCAL, GOOGLE
    }
}
