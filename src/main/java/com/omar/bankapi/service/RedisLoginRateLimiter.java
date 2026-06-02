package com.omar.bankapi.service;

import com.omar.bankapi.config.LoginRateLimitProperties;
import com.omar.bankapi.exception.LoginRateLimitExceededException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Locale;

public class RedisLoginRateLimiter implements LoginRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final LoginRateLimitProperties properties;

    public RedisLoginRateLimiter(StringRedisTemplate redisTemplate, LoginRateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public void registerAttempt(String username, String clientIp) {
        long userCount = increment(userKey(username, clientIp));
        long ipCount = increment(ipKey(clientIp));

        if (userCount > properties.getMaxAttempts() || ipCount > properties.getMaxAttempts()) {
            throw new LoginRateLimitExceededException(
                    "Too many login attempts. Please try again later."
            );
        }
    }

    @Override
    public void reset(String username, String clientIp) {
        redisTemplate.delete(userKey(username, clientIp));
        redisTemplate.delete(ipKey(clientIp));
    }

    private long increment(String key) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) {
            throw new IllegalStateException("Failed to increment login rate limit counter");
        }

        if (count == 1L) {
            Duration window = properties.getWindow();
            if (window != null) {
                redisTemplate.expire(key, window);
            }
        }

        return count;
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
}
