package com.omar.bankapi.service;

import com.omar.bankapi.config.LoginRateLimitProperties;
import com.omar.bankapi.exception.LoginRateLimitExceededException;

import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryLoginRateLimiter implements LoginRateLimiter {

    private final LoginRateLimitProperties properties;
    private final ConcurrentHashMap<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

    public InMemoryLoginRateLimiter(LoginRateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    public void registerAttempt(String username, String clientIp) {
        int userCount = increment(userKey(username, clientIp));
        int ipCount = increment(ipKey(clientIp));

        if (userCount > properties.getMaxAttempts() || ipCount > properties.getMaxAttempts()) {
            throw new LoginRateLimitExceededException(
                    "Too many login attempts. Please try again later."
            );
        }
    }

    @Override
    public void reset(String username, String clientIp) {
        attempts.remove(userKey(username, clientIp));
        attempts.remove(ipKey(clientIp));
    }

    private int increment(String key) {
        Instant now = Instant.now();
        AttemptWindow updated = attempts.compute(key, (ignored, current) -> {
            if (current == null || !current.expiresAt.isAfter(now)) {
                return new AttemptWindow(1, now.plus(properties.getWindow()));
            }

            current.count++;
            return current;
        });

        if (updated == null) {
            throw new IllegalStateException("Failed to update login rate limit counter");
        }

        return updated.count;
    }

    private String userKey(String username, String clientIp) {
        return "login-rate:user:" + normalize(username) + ":" + normalize(clientIp);
    }

    private String ipKey(String clientIp) {
        return "login-rate:ip:" + normalize(clientIp);
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class AttemptWindow {
        private int count;
        private final Instant expiresAt;

        private AttemptWindow(int count, Instant expiresAt) {
            this.count = count;
            this.expiresAt = expiresAt;
        }
    }
}
