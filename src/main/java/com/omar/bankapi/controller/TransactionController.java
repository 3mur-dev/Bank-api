package com.omar.bankapi.controller;

import com.omar.bankapi.dto.TransactionDTO;
import com.omar.bankapi.model.enums.TransactionStatus;
import com.omar.bankapi.model.enums.TransactionType;
import com.omar.bankapi.model.enums.OperationType;
import com.omar.bankapi.service.TransactionService;
import com.omar.bankapi.service.IdempotencyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.omar.bankapi.dto.*;

import java.util.function.Supplier;

@RestController
@AllArgsConstructor
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "Admin transaction review and lifecycle endpoints")
public class TransactionController {

    private final TransactionService transactionService;
    private final IdempotencyService idempotencyService;

    @GetMapping
        public ApiResponse<Page<TransactionDTO>> getAllTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) Boolean fraud,
            HttpServletRequest request) {

        Page<TransactionDTO> transactions = transactionService.getTransactions(page, size, accountId, status, type, fraud);

        return ApiResponse.ok(
                transactions,
                "Transactions retrevied successfully",
                request
        );
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<TransactionDTO> approveTransaction(@Positive @PathVariable Long id,
                                                           @RequestHeader(value = "X-Idempotency-Key", required = false) String xIdempotencyKey,
                                                           @RequestHeader(value = "Idempotency-Key", required = false) String legacyIdempotencyKey,
                                                           HttpServletRequest request) {
        return executeIdempotentReview(
                resolveIdempotencyKey(xIdempotencyKey, legacyIdempotencyKey),
                OperationType.APPROVE_TRANSACTION,
                id,
                "transaction approved successfully",
                () -> transactionService.approveTransaction(id),
                request
        );
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<TransactionDTO> rejectTransaction(@Positive @PathVariable Long id,
                                                         @RequestHeader(value = "X-Idempotency-Key", required = false) String xIdempotencyKey,
                                                         @RequestHeader(value = "Idempotency-Key", required = false) String legacyIdempotencyKey,
                                                         HttpServletRequest request) {
        return executeIdempotentReview(
                resolveIdempotencyKey(xIdempotencyKey, legacyIdempotencyKey),
                OperationType.REJECT_TRANSACTION,
                id,
                "transaction rejected successfully",
                () -> transactionService.rejectTransaction(id),
                request
        );
    }

    private ApiResponse<TransactionDTO> executeIdempotentReview(
            String idempotencyKey,
            OperationType operationType,
            Long transactionId,
            String successMessage,
            Supplier<TransactionDTO> action,
            HttpServletRequest request
    ) {
        TransactionDTO result = idempotencyService.execute(
                idempotencyKey,
                operationType,
                transactionId,
                null,
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
