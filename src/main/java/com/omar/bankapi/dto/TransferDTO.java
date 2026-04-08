package com.omar.bankapi.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransferDTO {
    @NotNull
    private Long receiver;

    @NotNull
    @Positive
    private BigDecimal amount;
}
