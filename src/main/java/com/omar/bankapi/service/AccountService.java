package com.omar.bankapi.service;

import com.omar.bankapi.dto.*;
import com.omar.bankapi.model.Account;
import com.omar.bankapi.model.Transaction;
import com.omar.bankapi.model.TransactionType;
import com.omar.bankapi.model.User;
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
        User user = userRepository.findById(dto.getUserId())
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
        account.setType(dto.getType());
        account.setUser(user);

        try {
            Account saved = accountRepository.save(account);
            return mapToCreateAccount(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account number already exists");
        }
    }

    public void deleteAccount(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Account not found"));
        requireAdminOrOwner(account);
        accountRepository.delete(account);
    }

    public AccountDTO updateAccount(Long id, UpdateAccountDTO dto) {

        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Account not found"));

        requireAdminOrOwner(account);
        account.setActive(dto.getIsActive());
        account.setType(dto.getType());

        Account updated = accountRepository.save(account);

        return mapToAccountDTO(updated);
    }

    @Transactional
    public AccountDTO deposit(Long id, TransactionAmountDTO dto) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Account not found"));
        requireAdminOrOwner(account);
        requireActive(account);

        BigDecimal amount = requirePositiveAmount(dto.getAmount());
        int updatedRows = accountRepository.incrementBalance(id, amount);
        if (updatedRows != 1) {
            Account current = accountRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Account not found"));
            if (!current.isActive()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account is inactive");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account update failed");
        }
        Account updated = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Account not found"));

        recordTransaction(updated, updated, amount, TransactionType.DEPOSIT);

        return mapToAccountDTO(updated);
    }

    @Transactional
    public AccountDTO withdraw(Long id, TransactionAmountDTO dto) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Account not found"));
        requireAdminOrOwner(account);
        requireActive(account);

        BigDecimal amount = requirePositiveAmount(dto.getAmount());
        int updatedRows = accountRepository.decrementBalance(id, amount);
        if (updatedRows != 1) {
            Account current = accountRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Account not found"));
            if (!current.isActive()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account is inactive");
            }
            if (current.getBalance().compareTo(amount) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient balance");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account update failed");
        }
        Account updated = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Account not found"));

        recordTransaction(updated, null, amount, TransactionType.WITHDRAW);

        return mapToAccountDTO(updated);
    }

    @Transactional
    public AccountDTO transfer(Long id, TransferDTO dto) {

        Account sender = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Sender account not found"));
        requireAdminOrOwner(sender);
        requireActive(sender);

        Long receiverId = dto.getReceiver();
        Account receiver = accountRepository.findById(receiverId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Receiver account not found"));
        requireActive(receiver);

        BigDecimal amount = requirePositiveAmount(dto.getAmount());

        if (sender.getId().equals(receiverId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot transfer to same account");
        }

        int senderUpdatedRows = accountRepository.decrementBalance(id, amount);
        if (senderUpdatedRows != 1) {
            Account currentSender = accountRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Sender account not found"));
            if (!currentSender.isActive()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account is inactive");
            }
            if (currentSender.getBalance().compareTo(amount) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient balance");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account update failed");
        }

        int receiverUpdatedRows = accountRepository.incrementBalance(receiverId, amount);
        if (receiverUpdatedRows != 1) {
            Account currentReceiver = accountRepository.findById(receiverId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Receiver account not found"));
            if (!currentReceiver.isActive()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Receiver account is inactive");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account update failed");
        }

        Account updatedSender = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Sender account not found"));
        Account updatedReceiver = accountRepository.findById(receiverId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Receiver account not found"));

        recordTransaction(updatedSender, updatedReceiver, amount, TransactionType.TRANSFER);

        return mapToAccountDTO(updatedSender);
    }

    private AccountDTO mapToAccountDTO(Account account) {
        AccountDTO dto = new AccountDTO();

        dto.setId(account.getId());
        dto.setAccountNumber(account.getAccountNumber());
        dto.setBalance(account.getBalance());
        dto.setType(account.getType());
        dto.setActive(account.isActive());
        dto.setUserId(account.getUser() != null ? account.getUser().getId() : null);

        return dto;
    }

    private CreateAccountDTO mapToCreateAccount(Account account) {

        CreateAccountDTO dto = new CreateAccountDTO();

        dto.setAccountNumber(account.getAccountNumber());
        dto.setType(account.getType());

        if (account.getUser() != null) {
            dto.setUserId(account.getUser().getId());
        }
        return dto;
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
        return userRepository.findByUsername(auth.getName())
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account is inactive");
        }
    }

    private BigDecimal requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be greater than zero");
        }
        return amount;
    }

    private void recordTransaction(Account sender, Account receiver, BigDecimal amount, TransactionType type) {
        Transaction transaction = new Transaction();
        transaction.setSender(sender);
        transaction.setReceiver(receiver);
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setSuccessful(true);
        transactionRepository.save(transaction);
    }
}
