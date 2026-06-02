package com.omar.bankapi.controller;

import com.omar.bankapi.dto.*;
import com.omar.bankapi.service.AccountService;
import com.omar.bankapi.service.IdempotencyService;
import com.omar.bankapi.model.enums.OperationType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.function.Supplier;

@RestController
@AllArgsConstructor
@RequestMapping("/api/accounts")
@Tag(name = "Accounts", description = "Account management and customer money-movement endpoints")
public class AccountController {

    private final AccountService accountService;
    private final IdempotencyService idempotencyService;

    @GetMapping
    public ApiResponse<List<AccountDTO>> getAllAccounts(HttpServletRequest request) {
        return ApiResponse.ok(accountService.getAllAccounts(), "Accounts retrieved successfully", request);
    }

    @GetMapping("/{id}")
    public ApiResponse<AccountDTO> getAccountById(@Positive @PathVariable Long id,
                                                  HttpServletRequest request) {
        return ApiResponse.ok(accountService.getAccountById(id), "Account retrieved successfully", request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateAccountDTO> createAccount(@Valid @RequestBody CreateAccountDTO dto,
                                                       HttpServletRequest request) {
        return ApiResponse.ok(accountService.createAccount(dto), "Account created successfully", request);
    }

    @PostMapping("/{id}/close")
    public ApiResponse<AccountDTO> closeAccount(@Positive @PathVariable Long id,
                                                HttpServletRequest request) {
        return ApiResponse.ok(
                accountService.closeAccount(id),
                "Account closed successfully",
                request
        );
    }

    @PutMapping("/{id}")
    public ApiResponse<AccountDTO> updateAccount(@Positive @PathVariable Long id,
                                                 @Valid @RequestBody UpdateAccountDTO dto,
                                                 HttpServletRequest request) {
        return ApiResponse.ok(accountService.updateAccount(id, dto), "Account updated successfully", request);
    }

    @PostMapping("/{id}/deposit")
    public ApiResponse<TransactionDTO> deposit(@Positive @PathVariable Long id,
                                               @Valid @RequestBody TransactionAmountDTO dto,
                                               @RequestHeader(value = "X-Idempotency-Key", required = false) String xIdempotencyKey,
                                               @RequestHeader(value = "Idempotency-Key", required = false) String legacyIdempotencyKey,
                                               HttpServletRequest request) {

        return executeIdempotentWrite(
                resolveIdempotencyKey(xIdempotencyKey, legacyIdempotencyKey),
                OperationType.DEPOSIT,
                id,
                dto,
                "Deposit successful",
                () -> accountService.deposit(id, dto),
                request
        );
    }

    @PostMapping("/{id}/withdraw")
    public ApiResponse<TransactionDTO> withdraw(@Positive @PathVariable Long id,
                                                @Valid @RequestBody TransactionAmountDTO dto,
                                                @RequestHeader(value = "X-Idempotency-Key", required = false) String xIdempotencyKey,
                                                @RequestHeader(value = "Idempotency-Key", required = false) String legacyIdempotencyKey,
                                                HttpServletRequest request) {
        return executeIdempotentWrite(
                resolveIdempotencyKey(xIdempotencyKey, legacyIdempotencyKey),
                OperationType.WITHDRAW,
                id,
                dto,
                "Withdrawal successful",
                () -> accountService.withdraw(id, dto),
                request
        );
    }

    @PostMapping("/{id}/transfer")
    public ApiResponse<TransactionDTO> transfer(@Positive @PathVariable Long id,
                                                @Valid @RequestBody TransferDTO dto,
                                                @RequestHeader(value = "X-Idempotency-Key", required = false) String xIdempotencyKey,
                                                @RequestHeader(value = "Idempotency-Key", required = false) String legacyIdempotencyKey,
                                                HttpServletRequest request) {
        return executeIdempotentWrite(
                resolveIdempotencyKey(xIdempotencyKey, legacyIdempotencyKey),
                OperationType.TRANSFER,
                id,
                dto,
                "Transfer successful",
                () -> accountService.transfer(id, dto),
                request
        );
    }

    private ApiResponse<TransactionDTO> executeIdempotentWrite(
            String idempotencyKey,
            OperationType operationType,
            Long accountId,
            Object requestBody,
            String successMessage,
            Supplier<TransactionDTO> action,
            HttpServletRequest request
    ) {
        TransactionDTO result = idempotencyService.execute(
                idempotencyKey,
                operationType,
                accountId,
                requestBody,
                successMessage,
                action
        );

        return ApiResponse.ok(result, successMessage, request);
    }

    private String resolveIdempotencyKey(String primaryHeader, String legacyHeader) {
        String primary = normalizeHeader(primaryHeader);
        String legacy = normalizeHeader(legacyHeader);

        if (primary == null && legacy == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.PRECONDITION_REQUIRED,
                    "Idempotency-Key header is required"
            );
        }

        if (primary != null && legacy != null && !primary.equals(legacy)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Conflicting idempotency key headers"
            );
        }

        return primary != null ? primary : legacy;
    }

    private String normalizeHeader(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency key cannot be blank"
            );
        }

        return trimmed;
    }
}
