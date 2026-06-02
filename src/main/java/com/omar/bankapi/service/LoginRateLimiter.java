package com.omar.bankapi.service;

public interface LoginRateLimiter {

    void registerAttempt(String username, String clientIp);

    void reset(String username, String clientIp);
}
