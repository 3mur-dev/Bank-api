package com.omar.bankapi.repository;

import com.omar.bankapi.model.IdempotencyKey;
import com.omar.bankapi.model.enums.IdempotencyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface IdempotencyRepository
        extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByIdempotencyKey(String idempotencyKey);

    long deleteByCreatedAtBefore(LocalDateTime cutoff);

    long deleteByStatusInAndCreatedAtBefore(Collection<IdempotencyStatus> statuses, LocalDateTime cutoff);
}
