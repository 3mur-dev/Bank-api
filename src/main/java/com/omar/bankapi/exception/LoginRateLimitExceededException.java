package com.omar.bankapi.exception;

public class LoginRateLimitExceededException extends RuntimeException {
    public LoginRateLimitExceededException(String message) {
        super(message);
    }
}
