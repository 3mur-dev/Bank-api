package com.omar.bankapi.dto;

import lombok.Data;

@Data
public class FraudDTO {

    private Boolean isFraud;

    private String fraudReason;

    private FraudAction action;
}