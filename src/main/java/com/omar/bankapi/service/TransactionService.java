package com.omar.bankapi.service;

import com.omar.bankapi.dto.*;
import com.omar.bankapi.exception.AccountNotFoundException;
import com.omar.bankapi.exception.InsufficientBalanceException;
import com.omar.bankapi.exception.TransactionAlreadyProcessedException;
import com.omar.bankapi.exception.TransactionNotFoundException;
import com.omar.bankapi.model.*;
import com.omar.bankapi.model.enums.TransactionStatus;
import com.omar.bankapi.model.enums.TransactionType;
import com.omar.bankapi.repository.AccountRepository;
import com.omar.bankapi.repository.TransactionRepository;
import com.omar.bankapi.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@AllArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

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

    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public TransactionDTO approveTransaction(Long id) {
        return approveTransactionLocked(id);
    }

    @Transactional
    public TransactionDTO rejectTransaction(Long id) {
        return reviewTransaction(id, ReviewDecision.REJECT);
    }

    private TransactionDTO approveTransactionLocked(Long id) {
        Authentication admin = requireAdmin();
        AdminActor actor = resolveAdminActor(admin);

        Transaction tx = loadTransactionForApproval(id, actor);
        ensurePending(tx, actor);

        LockedAccounts lockedAccounts = lockAccounts(tx, actor);
        Account sender = lockedAccounts.sender();
        Account receiver = lockedAccounts.receiver();

        BigDecimal amount = tx.getAmount();
        BigDecimal previousSenderBalance = sender.getBalance();
        BigDecimal previousReceiverBalance = receiver.getBalance();
        TransactionStatus oldStatus = tx.getStatus();
        LocalDateTime now = LocalDateTime.now();
        boolean sameAccount = sender.getId().equals(receiver.getId());

        if (previousSenderBalance.compareTo(amount) < 0) {
            auditFailure(
                    actor,
                    tx.getId(),
                    "Insufficient sender balance",
                    buildApprovalAuditData(
                            tx,
                            sender,
                            receiver,
                            amount,
                            previousSenderBalance,
                            previousSenderBalance,
                            previousReceiverBalance,
                            previousReceiverBalance,
                            oldStatus,
                            oldStatus,
                            actor,
                            now
                    )
            );
            throw new InsufficientBalanceException("Sender has insufficient balance");
        }

        BigDecimal newSenderBalance = sameAccount
                ? previousSenderBalance
                : previousSenderBalance.subtract(amount);
        BigDecimal newReceiverBalance = sameAccount
                ? previousReceiverBalance
                : previousReceiverBalance.add(amount);

        if (newSenderBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientBalanceException("Sender balance cannot become negative");
        }

        sender.setBalance(newSenderBalance);
        accountRepository.save(sender);
        if (!sameAccount) {
            receiver.setBalance(newReceiverBalance);
            accountRepository.save(receiver);
        }

        tx.setStatus(TransactionStatus.CONFIRMED);
        tx.setSuccessful(true);
        tx.setIsFlagged(false);
        tx.setFraudReason(null);
        tx.setApprovedAt(now);

        Transaction saved = transactionRepository.save(tx);

        auditService.recordActor(
                actor.username(),
                "APPROVE_TRANSACTION",
                "TRANSACTION",
                String.valueOf(saved.getId()),
                true,
                null,
                null,
                buildApprovalAuditData(
                        saved,
                        sender,
                        receiver,
                        amount,
                        previousSenderBalance,
                        newSenderBalance,
                        previousReceiverBalance,
                        newReceiverBalance,
                        oldStatus,
                        TransactionStatus.CONFIRMED,
                        actor,
                        now
                )
        );

        return mapToTransactionDTO(saved);
    }

    private Transaction loadTransactionForApproval(Long id, AdminActor actor) {
        return transactionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> {
                    auditFailure(actor, id, "Transaction not found", null);
                    return new TransactionNotFoundException("Transaction not found");
                });
    }

    private void ensurePending(Transaction tx, AdminActor actor) {
        if (tx.getStatus() != TransactionStatus.PENDING) {
            auditFailure(actor, tx.getId(), "Transaction is not pending", mapToTransactionDTO(tx));
            throw new TransactionAlreadyProcessedException("Transaction is not pending");
        }
    }

    private LockedAccounts lockAccounts(Transaction tx, AdminActor actor) {
        Long senderId = resolveAccountId(tx.getSender(), "Sender account not found", tx.getId(), actor);
        Long receiverId = resolveAccountId(tx.getReceiver(), "Receiver account not found", tx.getId(), actor);

        if (senderId.equals(receiverId)) {
            Account account = lockAccountForUpdate(senderId, tx.getId(), actor);
            return new LockedAccounts(account, account);
        }

        if (senderId < receiverId) {
            Account sender = lockAccountForUpdate(senderId, tx.getId(), actor);
            Account receiver = lockAccountForUpdate(receiverId, tx.getId(), actor);
            return new LockedAccounts(sender, receiver);
        }

        Account receiver = lockAccountForUpdate(receiverId, tx.getId(), actor);
        Account sender = lockAccountForUpdate(senderId, tx.getId(), actor);
        return new LockedAccounts(sender, receiver);
    }

    private Account lockAccountForUpdate(Long accountId, Long transactionId, AdminActor actor) {
        return accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> {
                    auditFailure(actor, transactionId, "Account not found", accountId);
                    return new AccountNotFoundException("Account not found");
                });
    }

    private Long resolveAccountId(Account account, String message, Long transactionId, AdminActor actor) {
        if (account == null || account.getId() == null) {
            auditFailure(actor, transactionId, message, null);
            throw new AccountNotFoundException(message);
        }
        return account.getId();
    }

    private AdminActor resolveAdminActor(Authentication authentication) {
        User user = userRepository.findByUsernameAndDeletedFalse(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Admin account not found"));
        return new AdminActor(user.getId(), user.getUsername());
    }

    private ApprovalAuditData buildApprovalAuditData(
            Transaction transaction,
            Account sender,
            Account receiver,
            BigDecimal amount,
            BigDecimal previousSenderBalance,
            BigDecimal newSenderBalance,
            BigDecimal previousReceiverBalance,
            BigDecimal newReceiverBalance,
            TransactionStatus oldStatus,
            TransactionStatus newStatus,
            AdminActor actor,
            LocalDateTime timestamp
    ) {
        return new ApprovalAuditData(
                transaction.getId(),
                sender.getId(),
                receiver.getId(),
                amount,
                previousSenderBalance,
                newSenderBalance,
                previousReceiverBalance,
                newReceiverBalance,
                oldStatus,
                newStatus,
                actor.id(),
                actor.username(),
                timestamp
        );
    }

    private void auditFailure(AdminActor actor, Long transactionId, String reason, Object beforeData) {
        auditService.recordFailureActor(
                actor.username(),
                "APPROVE_TRANSACTION",
                "TRANSACTION",
                String.valueOf(transactionId),
                reason,
                beforeData
        );
    }

    private record AdminActor(Long id, String username) {
    }

    private record LockedAccounts(Account sender, Account receiver) {
    }

    private record ApprovalAuditData(
            Long transactionId,
            Long senderId,
            Long receiverId,
            BigDecimal amount,
            BigDecimal previousSenderBalance,
            BigDecimal newSenderBalance,
            BigDecimal previousReceiverBalance,
            BigDecimal newReceiverBalance,
            TransactionStatus oldStatus,
            TransactionStatus newStatus,
            Long approvingAdminId,
            String approvingAdminUsername,
            LocalDateTime timestamp
    ) {
    }

    private TransactionDTO reviewTransaction(Long id, ReviewDecision decision) {
        requireAdmin();

        Transaction tx = loadReviewTarget(id, decision);
        ensurePending(tx, id, decision);

        Transaction saved = switch (decision) {
            case APPROVE -> approvePendingTransaction(tx, id);
            case REJECT -> rejectPendingTransaction(tx);
        };

        auditSuccess(decision, saved);
        return mapToTransactionDTO(saved);
    }

    private Transaction loadReviewTarget(Long id, ReviewDecision decision) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> {
                    auditFailure(decision, id, "Transaction not found", null);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found");
                });
    }

    private void ensurePending(Transaction tx, Long id, ReviewDecision decision) {
        if (tx.getStatus() != TransactionStatus.PENDING) {
            auditFailure(decision, id, "Transaction is not pending", mapToTransactionDTO(tx));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction is not pending");
        }
    }

    private Transaction approvePendingTransaction(Transaction tx, Long id) {
        BigDecimal amount = tx.getAmount();

        requireSufficientSenderBalance(tx, id, amount);
        debitSender(tx, id, amount);
        creditReceiver(tx, id, amount);

        tx.setStatus(TransactionStatus.CONFIRMED);
        tx.setIsFlagged(false);
        tx.setFraudReason(null);
        tx.setSuccessful(true);

        return transactionRepository.save(tx);
    }

    private Transaction rejectPendingTransaction(Transaction tx) {
        tx.setStatus(TransactionStatus.REJECTED);
        tx.setIsFlagged(true);
        tx.setSuccessful(false);

        if (tx.getFraudReason() == null) {
            tx.setFraudReason("Transaction rejected by admin");
        }

        return transactionRepository.save(tx);
    }

    private void requireSufficientSenderBalance(Transaction tx, Long id, BigDecimal amount) {
        if (tx.getSender().getBalance().compareTo(amount) < 0) {
            auditFailure(
                    ReviewDecision.APPROVE,
                    id,
                    "Sender has insufficient balance",
                    mapToTransactionDTO(tx)
            );
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sender has insufficient balance");
        }
    }

    private void debitSender(Transaction tx, Long id, BigDecimal amount) {
        int senderUpdated = accountRepository.decrementBalance(tx.getSender().getId(), amount);
        if (senderUpdated != 1) {
            auditFailure(
                    ReviewDecision.APPROVE,
                    id,
                    "Sender update failed",
                    mapToTransactionDTO(tx)
            );
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sender update failed");
        }
    }

    private void creditReceiver(Transaction tx, Long id, BigDecimal amount) {
        int receiverUpdated = accountRepository.incrementBalance(tx.getReceiver().getId(), amount);
        if (receiverUpdated != 1) {
            auditFailure(
                    ReviewDecision.APPROVE,
                    id,
                    "Receiver issue",
                    mapToTransactionDTO(tx)
            );
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Receiver issue");
        }
    }

    private void auditSuccess(ReviewDecision decision, Transaction saved) {
        auditService.record(
                decision.action(),
                "TRANSACTION",
                String.valueOf(saved.getId()),
                true,
                null,
                null,
                mapToTransactionDTO(saved)
        );
    }

    private void auditFailure(ReviewDecision decision, Long id, String reason, Object beforeData) {
        auditService.recordFailure(
                decision.action(),
                "TRANSACTION",
                String.valueOf(id),
                reason,
                beforeData
        );
    }

    public TransactionDTO mapToTransactionDTO(Transaction tx) {
        return new TransactionDTO(
                tx.getId(),
                tx.getSender() != null ? tx.getSender().getId() : null,
                tx.getReceiver() != null ? tx.getReceiver().getId() : null,
                tx.getAmount(),
                tx.getType(),
                tx.getStatus(),
                Boolean.TRUE.equals(tx.getIsFlagged()),
                tx.getFraudReason(),
                tx.getSuccessful()
        );
    }

    private Authentication requireAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Admin access required");
        }
        return auth;
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities()
                .stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private Authentication requireAdmin() {
        Authentication auth = requireAuth();
        if (!isAdmin(auth)) {
            throw new AccessDeniedException("Admin access required");
        }
        return auth;
    }

    private enum ReviewDecision {
        APPROVE("APPROVE_TRANSACTION"),
        REJECT("REJECT_TRANSACTION");

        private final String action;

        ReviewDecision(String action) {
            this.action = action;
        }

        private String action() {
            return action;
        }
    }
}
