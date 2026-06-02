package com.omar.bankapi.exception;

import com.omar.bankapi.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Helper
    private String traceId() {
        return MDC.get("traceId");
    }

    private ResponseEntity<ErrorResponse> build(
            String message,
            HttpStatus status,
            Map<String, List<String>> errors,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(
                new ErrorResponse(
                        message,
                        status.value(),
                        Instant.now(),
                        request.getRequestURI(),
                        traceId(),
                        errors
                )
        );
    }

    // VALIDATION ERRORS
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {

        log.debug("Validation error | traceId={} |", traceId());

        Map<String, List<String>> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.groupingBy(
                        FieldError::getField,
                        Collectors.mapping(FieldError::getDefaultMessage, Collectors.toList())
                ));

        return build(
                "Validation failed",
                HttpStatus.BAD_REQUEST,
                errors,
                request
        );
    }

    // AUTH / BUSINESS ERRORS

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex,
            HttpServletRequest request
    ) {
        return build(ex.getMessage(), HttpStatus.UNAUTHORIZED, null, request);
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUsernameExists(
            UsernameAlreadyExistsException ex,
            HttpServletRequest request
    ) {
        return build(ex.getMessage(), HttpStatus.CONFLICT, null, request);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailExists(
            EmailAlreadyExistsException ex,
            HttpServletRequest request
    ) {
        return build(ex.getMessage(), HttpStatus.CONFLICT, null, request);
    }

    // SPRING EXCEPTIONS
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            ResponseStatusException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;

        return build(
                ex.getReason() != null ? ex.getReason() : ex.getMessage(),
                status,
                null,
                request
        );
    }

    // SECURITY EXCEPTIONS

    @ExceptionHandler(ApiSecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurity(
            ApiSecurityException ex,
            HttpServletRequest request
    ) {
        log.warn("Auth failure | traceId={} | message={}", traceId(), ex.getMessage());

        return build(ex.getMessage(), ex.getStatus(), null, request);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(
            InvalidTokenException ex,
            HttpServletRequest request
    ) {
        log.warn("Auth failure | traceId={} | message={}", traceId(), ex.getMessage());

        return build(ex.getMessage(), HttpStatus.UNAUTHORIZED, null, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        return build(ex.getMessage(), HttpStatus.FORBIDDEN, null, request);
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTransactionNotFound(
            TransactionNotFoundException ex,
            HttpServletRequest request
    ) {
        return build(ex.getMessage(), HttpStatus.NOT_FOUND, null, request);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(
            AccountNotFoundException ex,
            HttpServletRequest request
    ) {
        return build(ex.getMessage(), HttpStatus.NOT_FOUND, null, request);
    }

    @ExceptionHandler(TransactionAlreadyProcessedException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyProcessed(
            TransactionAlreadyProcessedException ex,
            HttpServletRequest request
    ) {
        return build(ex.getMessage(), HttpStatus.CONFLICT, null, request);
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(
            InsufficientBalanceException ex,
            HttpServletRequest request
    ) {
        return build(ex.getMessage(), HttpStatus.BAD_REQUEST, null, request);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyConflict(
            IdempotencyConflictException ex,
            HttpServletRequest request
    ) {
        return build(ex.getMessage(), HttpStatus.CONFLICT, null, request);
    }

    @ExceptionHandler(LoginRateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleLoginRateLimitExceeded(
            LoginRateLimitExceededException ex,
            HttpServletRequest request
    ) {
        return build(ex.getMessage(), HttpStatus.TOO_MANY_REQUESTS, null, request);
    }

    // OPTIMISTIC LOCKING
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLocking(
            ObjectOptimisticLockingFailureException ex,
            HttpServletRequest request
    ) {
        log.warn("Optimistic locking conflict | traceId={}", traceId(), ex);

        return build(
                "Resource was modified concurrently. Please retry.",
                HttpStatus.CONFLICT,
                null,
                request
        );
    }

    // FALLBACK
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(
            Exception ex,
            HttpServletRequest request
    ) {

        log.error("Unhandled exception | traceId={}", traceId(), ex);

        return build(
                "Internal server error",
                HttpStatus.INTERNAL_SERVER_ERROR,
                null,
                request
        );
    }
}
