package com.omar.bankapi.dto;

import com.omar.bankapi.model.AccountType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AccountDTO {

    private Long id;
    private String accountNumber;
    private BigDecimal balance;
    private AccountType type;
    private boolean active;
    private Long userId;
}
