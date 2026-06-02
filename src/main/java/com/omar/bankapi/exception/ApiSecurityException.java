package com.omar.bankapi.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiSecurityException extends RuntimeException {

    private final HttpStatus status;

    public ApiSecurityException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}