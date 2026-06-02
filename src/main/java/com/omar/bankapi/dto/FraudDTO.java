package com.omar.bankapi.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FraudDTO(
        boolean isFraud,
        String fraudReason,
        FraudAction action
) {
        public static FraudDTO allow() {
                return new FraudDTO(false, null, FraudAction.ALLOW);
        }

        public static FraudDTO deny(String fraudReason) {
                return new FraudDTO(true, fraudReason, FraudAction.DENY);
        }

        public static FraudDTO pending(String fraudReason) {
                return new FraudDTO(true, fraudReason, FraudAction.PENDING);
        }
}
