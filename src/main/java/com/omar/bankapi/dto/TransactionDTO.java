package com.omar.bankapi.dto;

import com.omar.bankapi.model.TransactionStatus;
import com.omar.bankapi.model.TransactionType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransactionDTO {

    private Long id;

    private Long senderId;
    private Long receiverId;

    private BigDecimal amount;

    private TransactionType type;
    private TransactionStatus status;

    private boolean isFraud =  false;
    private String fraudReason = null;

    private Boolean successful;
}