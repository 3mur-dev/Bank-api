package com.omar.bankapi.dto;

import java.math.BigDecimal;

public record AdminStatsDTO(
        long totalUsers,
        long totalAccounts,
        long totalTransactions,
        BigDecimal totalBalances
) {
}
