package com.omar.bankapi.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransactionAmountDTO(
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than zero")
        @Digits(integer = 15, fraction = 4, message = "Amount must be a valid monetary value")
        BigDecimal amount
) {
}
