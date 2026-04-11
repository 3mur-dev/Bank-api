package com.omar.bankapi.service;

import com.omar.bankapi.dto.*;
import com.omar.bankapi.model.*;
import com.omar.bankapi.repository.AccountRepository;
import com.omar.bankapi.repository.TransactionRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@Service
@AllArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    public Page<TransactionDTO> getTransactions(
            int page,
            int size,
            Long accountId,
            TransactionStatus status,
            TransactionType type,
            Boolean fraud
    ) {

        requireAdmin();

        Specification<Transaction> spec =
                TransactionSpecification.filter(accountId, status, type, fraud);

        return transactionRepository.findAll(
                        spec,
                        PageRequest.of(page, size)
                )
                .map(this::mapToTransactionDTO);
    }

    @Transactional
    public TransactionDTO approveTransaction(Long id) {

        requireAdmin();

        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Transaction not found"));

        if (tx.getStatus() != TransactionStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction is not pending");
        }

        Account sender = tx.getSender();
        Account receiver = tx.getReceiver();
        BigDecimal amount = tx.getAmount();


        if (sender.getBalance().compareTo(amount) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Sender has insufficient balance");
        }


        int senderUpdated = accountRepository.decrementBalance(sender.getId(), amount);

        if (senderUpdated != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sender update failed");
        }

        int receiverUpdated = accountRepository.incrementBalance(receiver.getId(), amount);

        if (receiverUpdated != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Receiver issue");
        }

        // 🔥 UPDATE TRANSACTION
        tx.setStatus(TransactionStatus.CONFIRMED);
        tx.setIsFlagged(false);
        tx.setFraudReason(null);
        tx.setSuccessful(true);

        Transaction saved = transactionRepository.save(tx);

        return mapToTransactionDTO(saved);
    }

    @Transactional
    public TransactionDTO rejectTransaction(Long id) {
        requireAdmin();

        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Transaction not found"));

        if (tx.getStatus() != TransactionStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction is not pending");
        }

        tx.setStatus(TransactionStatus.REJECTED);
        tx.setIsFlagged(true);
        tx.setSuccessful(false);

        if (tx.getFraudReason() == null) {
            tx.setFraudReason("Transaction rejected by admin");
        }

        Transaction saved = transactionRepository.save(tx);

        return mapToTransactionDTO(saved);
    }

    public TransactionDTO mapToTransactionDTO(Transaction tx) {

        TransactionDTO dto = new TransactionDTO();

        dto.setId(tx.getId());
        dto.setAmount(tx.getAmount());
        dto.setType(tx.getType());
        dto.setStatus(tx.getStatus());
        dto.setFraud(tx.getIsFlagged() != null && tx.getIsFlagged());
        dto.setFraudReason(tx.getFraudReason());
        dto.setSuccessful(tx.getSuccessful());

        if (tx.getSender() != null) {
            dto.setSenderId(tx.getSender().getId());
        }

        if (tx.getReceiver() != null) {
            dto.setReceiverId(tx.getReceiver().getId());
        }

        return dto;
    }

    private Authentication requireAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return auth;
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities()
                .stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private void requireAdmin() {
        if (!isAdmin(requireAuth())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }
}
