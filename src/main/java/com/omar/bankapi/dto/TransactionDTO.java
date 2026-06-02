package com.omar.bankapi.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.omar.bankapi.model.enums.TransactionStatus;
import com.omar.bankapi.model.enums.TransactionType;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionDTO(
        Long id,
        Long senderId,
        Long receiverId,
        BigDecimal amount,
        TransactionType type,
        TransactionStatus status,
        boolean isFraud,
        String fraudReason,
        Boolean successful
) {
}
