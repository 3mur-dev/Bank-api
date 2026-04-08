package com.omar.bankapi.dto;

import com.omar.bankapi.model.AccountType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateAccountDTO {

    @NotNull(message = "Active status is required")
    private Boolean isActive;

    @NotNull(message = "Account type is required")
    private AccountType type;
}
