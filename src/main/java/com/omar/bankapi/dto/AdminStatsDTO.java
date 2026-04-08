package com.omar.bankapi.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdminStatsDTO {

    private long totalUsers;
    private long totalAccounts;
    private long totalTransactions;
    private BigDecimal totalBalances;
}
