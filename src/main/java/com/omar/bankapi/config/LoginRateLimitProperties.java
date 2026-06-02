package com.omar.bankapi.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.login-rate-limit")
@Validated
public class LoginRateLimitProperties {

    @NotBlank
    private String backend = "memory";

    @Min(1)
    private int maxAttempts = 5;

    private Duration window = Duration.ofMinutes(10);

    private Duration cleanupDelay = Duration.ofMinutes(15);

    public String getBackend() {
        return backend;
    }

    public void setBackend(String backend) {
        this.backend = backend;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getWindow() {
        return window;
    }

    public void setWindow(Duration window) {
        this.window = window;
    }

    public Duration getCleanupDelay() {
        return cleanupDelay;
    }

    public void setCleanupDelay(Duration cleanupDelay) {
        this.cleanupDelay = cleanupDelay;
    }
}
