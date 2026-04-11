package com.omar.bankapi.service;

import com.omar.bankapi.dto.FraudAction;
import com.omar.bankapi.dto.FraudDTO;
import com.omar.bankapi.model.Account;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class FraudService {


    public static final BigDecimal FRAUD_AMOUNT_THRESHOLD = BigDecimal.valueOf(5000);

    public FraudDTO checkFraud(BigDecimal amount, Account sender, Account receiver) {

        FraudDTO dto = new FraudDTO();

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            dto.setFraudReason("Invalid transfer amount");
            dto.setIsFraud(true);
            dto.setAction(FraudAction.DENY);
            return dto;
        }

        if (sender == null || receiver == null) {
            dto.setFraudReason("Invalid transfer accounts");
            dto.setIsFraud(true);
            dto.setAction(FraudAction.DENY);
            return dto;
        }

        if (sender.getId() != null && sender.getId().equals(receiver.getId())) {
            dto.setFraudReason("Cannot transfer to same account");
            dto.setIsFraud(true);
            dto.setAction(FraudAction.DENY);
            return dto;
        }

        if (!sender.isActive() || !receiver.isActive()) {
            dto.setFraudReason("One of the accounts is inactive");
            dto.setIsFraud(true);
            dto.setAction(FraudAction.DENY);
            return dto;
        }

        if (sender.getBalance() != null && sender.getBalance().compareTo(amount) < 0) {
            dto.setFraudReason("Insufficient sender balance");
            dto.setIsFraud(true);
            dto.setAction(FraudAction.DENY);
            return dto;
        }

        if (amount.compareTo(FRAUD_AMOUNT_THRESHOLD) > 0) {

            dto.setFraudReason("Transaction amount exceeds threshold");
            dto.setIsFraud(true);
            dto.setAction(FraudAction.PENDING);
            return dto;
        }

        dto.setIsFraud(false);
        dto.setAction(FraudAction.ALLOW);
        return dto;
    }

}
