package com.omar.bankapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omar.bankapi.dto.TransactionDTO;
import com.omar.bankapi.exception.IdempotencyConflictException;
import com.omar.bankapi.model.IdempotencyKey;
import com.omar.bankapi.model.Transaction;
import com.omar.bankapi.model.enums.IdempotencyStatus;
import com.omar.bankapi.model.enums.OperationType;
import com.omar.bankapi.repository.IdempotencyRepository;
import com.omar.bankapi.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.EnumSet;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final int MIN_KEY_LENGTH = 8;
    private static final int MAX_KEY_LENGTH = 255;

    private final IdempotencyRepository idempotencyRepository;
    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    public TransactionDTO execute(
            String idempotencyKey,
            OperationType operationType,
            Long resourceId,
            Object requestBody,
            String successMessage,
            Supplier<TransactionDTO> action
    ) {
        String normalizedKey = normalizeKey(idempotencyKey);
        if (normalizedKey == null) {
            throw new ResponseStatusException(
                    HttpStatus.PRECONDITION_REQUIRED,
                    "Idempotency-Key header is required"
            );
        }

        String actorIdentifier = resolveActorIdentifier();
        String requestHash = requestHash(operationType, resourceId, actorIdentifier, requestBody);

        Reservation reservation = reserve(normalizedKey, operationType, actorIdentifier, requestHash);

        if (reservation.cachedResponse() != null) {
            return reservation.cachedResponse();
        }

        if (reservation.cachedFailure() != null) {
            throw reservation.cachedFailure();
        }

        try {
            TransactionDTO result = action.get();
            complete(reservation.recordId(), result, successMessage);
            return result;
        } catch (RuntimeException ex) {
            fail(reservation.recordId(), ex);
            throw ex;
        }
    }

    private Reservation reserve(
            String idempotencyKey,
            OperationType operationType,
            String actorIdentifier,
            String requestHash
    ) {
        return newRequiresNewTemplate().execute(status -> {
            LocalDateTime now = LocalDateTime.now();
            IdempotencyKey existing = idempotencyRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
            if (existing != null && existing.getStatus() != IdempotencyStatus.PROCESSING && isExpired(existing, now)) {
                idempotencyRepository.delete(existing);
                idempotencyRepository.flush();
                existing = null;
            }

            if (existing != null) {
                validateExisting(existing, operationType, actorIdentifier, requestHash);
                return switch (existing.getStatus()) {
                    case COMPLETED -> Reservation.completed(existing.getId(), readCachedTransaction(existing));
                    case FAILED -> Reservation.failed(existing.getId(), failure(existing));
                    case PROCESSING -> throw new IdempotencyConflictException("Request is already being processed");
                };
            }

            IdempotencyKey created = new IdempotencyKey();
            created.setIdempotencyKey(idempotencyKey);
            created.setOperationType(operationType);
            created.setActorIdentifier(actorIdentifier);
            created.setRequestHash(requestHash);
            created.setStatus(IdempotencyStatus.PROCESSING);

            try {
                IdempotencyKey saved = idempotencyRepository.saveAndFlush(created);
                return Reservation.processing(saved.getId());
            } catch (DataIntegrityViolationException ex) {
                IdempotencyKey raced = idempotencyRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
                if (raced == null) {
                    throw new IdempotencyConflictException("Idempotency key is already in use");
                }
                if (raced.getStatus() != IdempotencyStatus.PROCESSING && isExpired(raced, now)) {
                    idempotencyRepository.delete(raced);
                    idempotencyRepository.flush();
                    return Reservation.processing(createFreshReservation(idempotencyKey, operationType, actorIdentifier, requestHash));
                }
                validateExisting(raced, operationType, actorIdentifier, requestHash);
                return switch (raced.getStatus()) {
                    case COMPLETED -> Reservation.completed(raced.getId(), readCachedTransaction(raced));
                    case FAILED -> Reservation.failed(raced.getId(), failure(raced));
                    case PROCESSING -> throw new IdempotencyConflictException("Request is already being processed");
                };
            }
        });
    }

    private Long createFreshReservation(
            String idempotencyKey,
            OperationType operationType,
            String actorIdentifier,
            String requestHash
    ) {
        IdempotencyKey created = new IdempotencyKey();
        created.setIdempotencyKey(idempotencyKey);
        created.setOperationType(operationType);
        created.setActorIdentifier(actorIdentifier);
        created.setRequestHash(requestHash);
        created.setStatus(IdempotencyStatus.PROCESSING);

        IdempotencyKey saved = idempotencyRepository.saveAndFlush(created);
        return saved.getId();
    }

    private void complete(Long recordId, TransactionDTO result, String successMessage) {
        runInNewTransaction(() -> {
            IdempotencyKey record = idempotencyRepository.findById(recordId)
                    .orElseThrow(() -> new IllegalStateException("Idempotency record not found"));

            record.setStatus(IdempotencyStatus.COMPLETED);
            record.setResponseData(serialize(result));
            record.setResponseStatusCode(HttpStatus.OK.value());
            record.setResponseMessage(successMessage);

            if (result.id() != null) {
                Transaction transaction = transactionRepository.findById(result.id())
                        .orElseThrow(() -> new IllegalStateException("Transaction not found"));
                record.setTransaction(transaction);
            }

            idempotencyRepository.save(record);
        });
    }

    private void fail(Long recordId, RuntimeException ex) {
        runInNewTransaction(() -> {
            IdempotencyKey record = idempotencyRepository.findById(recordId)
                    .orElseThrow(() -> new IllegalStateException("Idempotency record not found"));

            record.setStatus(IdempotencyStatus.FAILED);
            record.setResponseStatusCode(resolveStatus(ex).value());
            record.setResponseMessage(resolveMessage(ex));

            idempotencyRepository.save(record);
        });
    }

    private void runInNewTransaction(Runnable runnable) {
        newRequiresNewTemplate().executeWithoutResult(status -> runnable.run());
    }

    @Scheduled(fixedDelayString = "${app.idempotency.cleanup-delay-ms:900000}")
    @Transactional
    public void cleanupExpiredRecords() {
        LocalDateTime cutoff = LocalDateTime.now().minus(IDEMPOTENCY_TTL);
        idempotencyRepository.deleteByStatusInAndCreatedAtBefore(
                EnumSet.of(IdempotencyStatus.COMPLETED, IdempotencyStatus.FAILED),
                cutoff
        );
    }

    private TransactionTemplate newRequiresNewTemplate() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private TransactionDTO readCachedTransaction(IdempotencyKey existing) {
        if (existing.getResponseData() == null || existing.getResponseData().isBlank()) {
            throw new IllegalStateException("Completed idempotency record is missing response data");
        }

        try {
            return objectMapper.readValue(existing.getResponseData(), TransactionDTO.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize cached transaction", ex);
        }
    }

    private ResponseStatusException failure(IdempotencyKey existing) {
        HttpStatus status = HttpStatus.resolve(existing.getResponseStatusCode() != null
                ? existing.getResponseStatusCode()
                : HttpStatus.INTERNAL_SERVER_ERROR.value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        String message = existing.getResponseMessage() != null
                ? existing.getResponseMessage()
                : "Previous request failed";
        return new ResponseStatusException(status, message);
    }

    private void validateExisting(
            IdempotencyKey existing,
            OperationType operationType,
            String actorIdentifier,
            String requestHash
    ) {
        if (!existing.getOperationType().equals(operationType)
                || !existing.getActorIdentifier().equals(actorIdentifier)
                || !existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(
                    "Idempotency key was already used for a different request"
            );
        }
    }

    private String normalizeKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }

        String trimmed = idempotencyKey.trim();
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key cannot be blank");
        }

        if (trimmed.length() < MIN_KEY_LENGTH || trimmed.length() > MAX_KEY_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must be between " + MIN_KEY_LENGTH + " and " + MAX_KEY_LENGTH + " characters"
            );
        }

        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (ch < 33 || ch > 126) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Idempotency-Key must contain visible ASCII characters only"
                );
            }
        }

        return trimmed;
    }

    private String resolveActorIdentifier() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }
        return auth.getName();
    }

    private String requestHash(
            OperationType operationType,
            Long resourceId,
            String actorIdentifier,
            Object requestBody
    ) {
        try {
            String body = objectMapper.writeValueAsString(requestBody);
            String material = operationType.name() + ":" + resourceId + ":" + actorIdentifier + ":" + body;
            return sha256(material);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize idempotency request", ex);
        }
    }

    private String serialize(TransactionDTO result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize cached transaction", ex);
        }
    }

    private String resolveMessage(RuntimeException ex) {
        if (ex instanceof ResponseStatusException responseStatusException) {
            return responseStatusException.getReason() != null
                    ? responseStatusException.getReason()
                    : responseStatusException.getMessage();
        }
        return ex.getMessage() != null ? ex.getMessage() : "Unexpected failure";
    }

    private HttpStatus resolveStatus(RuntimeException ex) {
        if (ex instanceof ResponseStatusException responseStatusException) {
            HttpStatus resolved = HttpStatus.resolve(responseStatusException.getStatusCode().value());
            if (resolved != null) {
                return resolved;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private record Reservation(Long recordId, TransactionDTO cachedResponse, ResponseStatusException cachedFailure) {
        private static Reservation processing(Long recordId) {
            return new Reservation(recordId, null, null);
        }

        private static Reservation completed(Long recordId, TransactionDTO cachedResponse) {
            return new Reservation(recordId, cachedResponse, null);
        }

        private static Reservation failed(Long recordId, ResponseStatusException failure) {
            return new Reservation(recordId, null, failure);
        }
    }

    private boolean isExpired(IdempotencyKey record, LocalDateTime now) {
        return record.getCreatedAt() != null && record.getCreatedAt().isBefore(now.minus(IDEMPOTENCY_TTL));
    }
}
