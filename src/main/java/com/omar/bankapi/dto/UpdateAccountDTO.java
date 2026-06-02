package com.omar.bankapi.dto;

import com.omar.bankapi.model.enums.AccountType;
import jakarta.validation.constraints.NotNull;

public record UpdateAccountDTO(
        @NotNull(message = "Account type is required")
        AccountType type
) {
}
