package com.omar.bankapi.service;

import com.omar.bankapi.dto.*;
import com.omar.bankapi.model.*;
import com.omar.bankapi.model.enums.AccountType;
import com.omar.bankapi.model.enums.TransactionStatus;
import com.omar.bankapi.model.enums.TransactionType;
import com.omar.bankapi.repository.AccountRepository;
import com.omar.bankapi.repository.TransactionRepository;
import com.omar.bankapi.repository.UserRepository;
import com.omar.bankapi.util.AccountNumberGenerator;
import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class AccountService {

    private static final int ACCOUNT_NUMBER_RETRY_LIMIT = 5;

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final FraudService fraudService;
    private final AuditService auditService;

    public List<AccountDTO> getAllAccounts() {
        requireAdmin();
        return accountRepository.findAll().stream()
                .map(this::mapToAccountDTO)
                .collect(Collectors.toList());

    }

    public AccountDTO getAccountById(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        requireAdminOrOwner(account);

        return mapToAccountDTO(account);
    }

    public CreateAccountDTO createAccount(CreateAccountDTO dto) {

        Authentication auth = requireAuth();
        User user = userRepository.findByIdAndDeletedFalse(dto.userId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));

        if (!isAdmin(auth)) {
            User currentUser = requireCurrentUser(auth);
            if (!currentUser.getId().equals(user.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
            }
        }

        String accountNumber = generateUniqueAccountNumber();

        Account account = new Account();

        account.setAccountNumber(accountNumber);
        account.setType(dto.type());
        account.setUser(user);

        try {
            Account saved = accountRepository.save(account);
            auditService.record(
                    "CREATE_ACCOUNT",
                    "ACCOUNT",
                    String.valueOf(saved.getId()),
                    true,
                    null,
                    null,
                    mapToAccountDTO(saved)
            );
            return mapToCreateAccount(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account number already exists");
        }
    }

    @Transactional
    public AccountDTO closeAccount(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Account not found"));
        requireAdminOrOwner(account);

        if (!account.isActive()) {
            return mapToAccountDTO(account);
        }

        if (account.getBalance() != null && account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Account with a non-zero balance cannot be closed"
            );
        }

        if (transactionRepository.existsByAccountIdsAndStatus(List.of(id), TransactionStatus.PENDING)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Account with pending transactions cannot be closed"
            );
        }

        AccountDTO before = mapToAccountDTO(account);
        account.setActive(false);
        account.setClosedAt(java.time.LocalDateTime.now());
        Account saved = accountRepository.save(account);

        auditService.record(
                "CLOSE_ACCOUNT",
                "ACCOUNT",
                String.valueOf(id),
                true,
                null,
                before,
                mapToAccountDTO(saved)
        );

        return mapToAccountDTO(saved);
    }

    public AccountDTO updateAccount(Long id, UpdateAccountDTO dto) {

        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Account not found"));

        requireAdminOrOwner(account);
        account.setType(dto.type());

        Account updated = accountRepository.save(account);

        auditService.record(
                "UPDATE_ACCOUNT",
                "ACCOUNT",
                String.valueOf(updated.getId()),
                true,
                null,
                null,
                mapToAccountDTO(updated)
        );

        return mapToAccountDTO(updated);
    }

    @Transactional(rollbackFor = Exception.class)
    public TransactionDTO deposit(Long id, TransactionAmountDTO dto) {
        BigDecimal amount = requirePositiveAmount(dto.amount());

        Account account = loadAccountForUpdate(id, "Account not found");
        requireAdminOrOwner(account);
        requireActive(account);

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        Transaction tx = recordTransaction(account, account, amount, TransactionType.DEPOSIT);

        auditService.record(
                "DEPOSIT",
                "ACCOUNT",
                String.valueOf(id),
                true,
                null,
                null,
                mapToTransactionDTO(tx)
        );

        return mapToTransactionDTO(tx);
    }

    @Transactional(rollbackFor = Exception.class)
    public TransactionDTO withdraw(Long id, TransactionAmountDTO dto) {
        BigDecimal amount = requirePositiveAmount(dto.amount());

        Account account = loadAccountForUpdate(id, "Account not found");
        requireAdminOrOwner(account);
        requireActive(account);

        if (account.getBalance().compareTo(amount) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient balance");
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        Transaction tx = recordTransaction(account, account, amount, TransactionType.WITHDRAW);

        auditService.record(
                "WITHDRAW",
                "ACCOUNT",
                String.valueOf(id),
                true,
                null,
                null,
                mapToTransactionDTO(tx)
        );

        return mapToTransactionDTO(tx);
    }

    @Transactional(rollbackFor = Exception.class)
    public TransactionDTO transfer(Long id, TransferDTO dto) {
        BigDecimal amount = requirePositiveAmount(dto.amount());
        LockedTransferAccounts lockedAccounts = lockTransferAccounts(id, dto.receiver());
        Account sender = lockedAccounts.sender();
        Account receiver = lockedAccounts.receiver();

        requireAdminOrOwner(sender);
        requireActive(sender);
        requireActive(receiver);

        if (sender.getId().equals(receiver.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Cannot transfer to same account");
        }

        // ================= FRAUD CHECK =================
        FraudDTO fraud = fraudService.checkFraud(amount, sender, receiver);

        if (fraud.action() == FraudAction.PENDING) {

            Transaction fraudTx = new Transaction();
            fraudTx.setSender(sender);
            fraudTx.setReceiver(receiver);
            fraudTx.setAmount(amount);
            fraudTx.setType(TransactionType.TRANSFER);
            fraudTx.setStatus(TransactionStatus.PENDING);
            fraudTx.setIsFlagged(true);
            fraudTx.setFraudReason(fraud.fraudReason());
            fraudTx.setSuccessful(false);

            Transaction saved = transactionRepository.save(fraudTx);

            auditService.record(
                    "TRANSFER",
                    "ACCOUNT",
                    String.valueOf(id),
                    true,
                    fraud.fraudReason(),
                    null,
                    mapToTransactionDTO(saved)
            );

            return mapToTransactionDTO(saved);
        }

        if (fraud.action() == FraudAction.DENY) {
            auditService.record(
                    "TRANSFER",
                    "ACCOUNT",
                    String.valueOf(id),
                    false,
                    fraud.fraudReason() != null ? fraud.fraudReason() : "Transfer denied by fraud checks",
                    null,
                    null
            );
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fraud.fraudReason() != null ? fraud.fraudReason() : "Transfer denied by fraud checks"
            );
        }

        // ================= EXECUTE TRANSFER =================

        if (sender.getBalance().compareTo(amount) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient balance");
        }

        sender.setBalance(sender.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));
        accountRepository.save(sender);
        accountRepository.save(receiver);

        // ================= SAVE TRANSACTION =================

        Transaction tx = new Transaction();
        tx.setSender(sender);
        tx.setReceiver(receiver);
        tx.setAmount(amount);
        tx.setType(TransactionType.TRANSFER);
        tx.setStatus(TransactionStatus.CONFIRMED);
        tx.setIsFlagged(false);
        tx.setSuccessful(true);

        Transaction savedTx = transactionRepository.save(tx);

        auditService.record(
                "TRANSFER",
                "ACCOUNT",
                String.valueOf(id),
                true,
                null,
                null,
                mapToTransactionDTO(savedTx)
        );

        return mapToTransactionDTO(savedTx);
    }

    private AccountDTO mapToAccountDTO(Account account) {
        return new AccountDTO(
                account.getId(),
                account.getAccountNumber(),
                account.getBalance(),
                account.getType(),
                account.isActive(),
                account.getUser() != null ? account.getUser().getId() : null
        );
    }

    private CreateAccountDTO mapToCreateAccount(Account account) {
        return new CreateAccountDTO(
                account.getAccountNumber(),
                account.getType(),
                account.getUser() != null ? account.getUser().getId() : null
        );
    }

    private TransactionDTO mapToTransactionDTO(Transaction tx) {
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

    private String generateUniqueAccountNumber() {
        int attempts = 0;
        while (attempts < ACCOUNT_NUMBER_RETRY_LIMIT) {
            attempts++;
            String accountNumber = AccountNumberGenerator.generate();
            if (!accountRepository.existsByAccountNumber(accountNumber)) {
                return accountNumber;
            }
        }
        throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Failed to generate a unique account number");
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
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private User requireCurrentUser(Authentication auth) {
        return userRepository.findByUsernameAndDeletedFalse(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
    }

    private void requireAdmin() {
        if (!isAdmin(requireAuth())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }

    private void requireAdminOrOwner(Account account) {
        Authentication auth = requireAuth();
        if (isAdmin(auth)) {
            return;
        }
        User currentUser = requireCurrentUser(auth);
        if (account.getUser() == null || !account.getUser().getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }

    private void requireActive(Account account) {
        if (!account.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account is closed");
        }
    }

    private BigDecimal requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be greater than zero");
        }
        return amount;
    }

    private Account loadAccountForUpdate(Long id, String notFoundMessage) {
        return accountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, notFoundMessage));
    }

    private LockedTransferAccounts lockTransferAccounts(Long senderId, Long receiverId) {
        if (senderId.equals(receiverId)) {
            Account account = loadAccountForUpdate(senderId, "Sender account not found");
            return new LockedTransferAccounts(account, account);
        }

        Long firstId = senderId < receiverId ? senderId : receiverId;
        Long secondId = senderId < receiverId ? receiverId : senderId;

        boolean firstIsSender = firstId.equals(senderId);
        boolean secondIsSender = secondId.equals(senderId);

        Account first = loadAccountForUpdate(
                firstId,
                firstIsSender ? "Sender account not found" : "Receiver account not found"
        );
        Account second = loadAccountForUpdate(
                secondId,
                secondIsSender ? "Sender account not found" : "Receiver account not found"
        );

        Account sender = firstIsSender ? first : second;
        Account receiver = firstIsSender ? second : first;
        return new LockedTransferAccounts(sender, receiver);
    }

    private Transaction recordTransaction(Account sender, Account receiver, BigDecimal amount, TransactionType type) {
        Account resolvedSender = sender != null ? sender : receiver;
        Account resolvedReceiver = receiver != null ? receiver : sender;

        if (resolvedSender == null || resolvedReceiver == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Transaction participants could not be resolved"
            );
        }

        Transaction transaction = new Transaction();
        transaction.setSender(resolvedSender);
        transaction.setReceiver(resolvedReceiver);
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setStatus(TransactionStatus.CONFIRMED);
        transaction.setIsFlagged(false);
        transaction.setSuccessful(true);

        return transactionRepository.save(transaction);
    }

    private record LockedTransferAccounts(Account sender, Account receiver) {
    }

    public void createDefaultAccount(User savedUser) {
        {
            int attempts = 0;

            while (attempts < ACCOUNT_NUMBER_RETRY_LIMIT) {

                attempts++;

                Account account = new Account();
                account.setAccountNumber(AccountNumberGenerator.generate());
                account.setBalance(BigDecimal.ZERO);
                account.setUser(savedUser);
                account.setType(AccountType.CHECKING);

                try {

                    accountRepository.save(account);
                    auditService.recordSystem(
                            "CREATE_DEFAULT_ACCOUNT",
                            "ACCOUNT",
                            String.valueOf(account.getId()),
                            true,
                            null,
                            null,
                            mapToAccountDTO(account)
                    );
                    return;

                } catch (DataIntegrityViolationException ex) {

                    if (attempts >= ACCOUNT_NUMBER_RETRY_LIMIT) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Failed to generate a unique account number"
                        );
                    }
                }
            }
        }
    }
}
