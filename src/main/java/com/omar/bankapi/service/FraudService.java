package com.omar.bankapi.service;

import com.omar.bankapi.dto.FraudDTO;
import com.omar.bankapi.model.Account;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class FraudService {

    private final BigDecimal fraudAmountThreshold;

    public FraudService(@Value("${fraud.amount-threshold:5000}") BigDecimal fraudAmountThreshold) {
        this.fraudAmountThreshold = fraudAmountThreshold;
    }

    public FraudDTO checkFraud(BigDecimal amount, Account sender, Account receiver) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return FraudDTO.deny("Invalid transfer amount");
        }

        if (sender == null || receiver == null) {
            return FraudDTO.deny("Invalid transfer accounts");
        }

        if (sender.getId() != null && sender.getId().equals(receiver.getId())) {
            return FraudDTO.deny("Cannot transfer to same account");
        }

        if (!sender.isActive() || !receiver.isActive()) {
            return FraudDTO.deny("One of the accounts is closed");
        }

        if (sender.getBalance() != null && sender.getBalance().compareTo(amount) < 0) {
            return FraudDTO.deny("Insufficient sender balance");
        }

        if (amount.compareTo(fraudAmountThreshold) > 0) {
            return FraudDTO.pending("Transaction amount exceeds threshold");
        }

        return FraudDTO.allow();
    }
}
