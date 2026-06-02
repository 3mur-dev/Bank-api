package com.omar.bankapi.dto;

import com.omar.bankapi.model.enums.AccountType;

import java.math.BigDecimal;

public record AccountDTO(
        Long id,
        String accountNumber,
        BigDecimal balance,
        AccountType type,
        boolean active,
        Long userId
) {
}
