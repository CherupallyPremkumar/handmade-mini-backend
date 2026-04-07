package com.pochampally.config;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Simple in-memory rate limiter for login attempts.
 * Allows at most MAX_ATTEMPTS per IP within the WINDOW_SECONDS period.
 * Entries are lazily cleaned up on each check.
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final int WINDOW_SECONDS = 60;

    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Instant>> attempts = new ConcurrentHashMap<>();

    /**
     * Check if the given IP is allowed to attempt login.
     * If allowed, records the attempt and returns true.
     * If rate-limited, returns false.
     */
    public boolean tryAcquire(String ipAddress) {
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(WINDOW_SECONDS);

        ConcurrentLinkedDeque<Instant> timestamps = attempts.computeIfAbsent(ipAddress,
                k -> new ConcurrentLinkedDeque<>());

        // Evict expired entries
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= MAX_ATTEMPTS) {
            return false;
        }

        timestamps.addLast(now);
        return true;
    }

    /**
     * Returns the number of seconds until the next attempt window opens for this IP.
     */
    /** Clear all recorded attempts. Used in tests. */
    public void reset() {
        attempts.clear();
    }

    public long retryAfterSeconds(String ipAddress) {
        ConcurrentLinkedDeque<Instant> timestamps = attempts.get(ipAddress);
        if (timestamps == null || timestamps.isEmpty()) {
            return 0;
        }
        Instant oldest = timestamps.peekFirst();
        long elapsed = Instant.now().getEpochSecond() - oldest.getEpochSecond();
        return Math.max(0, WINDOW_SECONDS - elapsed);
    }
}
