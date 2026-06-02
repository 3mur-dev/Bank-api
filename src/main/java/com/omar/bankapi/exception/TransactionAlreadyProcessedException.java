package com.omar.bankapi.exception;

public class TransactionAlreadyProcessedException extends RuntimeException {

    public TransactionAlreadyProcessedException(String message) {
        super(message);
    }
}
