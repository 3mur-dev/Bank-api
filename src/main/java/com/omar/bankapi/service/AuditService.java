package com.omar.bankapi.service;

import com.omar.bankapi.event.AuditEventMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final ApplicationEventPublisher eventPublisher;

    public void record(
            String action,
            String targetType,
            String targetId,
            boolean success,
            String reason,
            Object beforeData,
            Object afterData
    ) {
        publishAfterCommit(new AuditEventMessage(
                action,
                targetType,
                targetId,
                success,
                reason,
                beforeData,
                afterData,
                null
        ));
    }

    public void recordFailure(
            String action,
            String targetType,
            String targetId,
            String reason,
            Object beforeData
    ) {
        eventPublisher.publishEvent(new AuditEventMessage(
                action,
                targetType,
                targetId,
                false,
                reason,
                beforeData,
                null,
                null
        ));
    }

    public void recordActor(
            String actorUsername,
            String action,
            String targetType,
            String targetId,
            boolean success,
            String reason,
            Object beforeData,
            Object afterData
    ) {
        publishAfterCommit(new AuditEventMessage(
                action,
                targetType,
                targetId,
                success,
                reason,
                beforeData,
                afterData,
                actorUsername
        ));
    }

    public void recordFailureActor(
            String actorUsername,
            String action,
            String targetType,
            String targetId,
            String reason,
            Object beforeData
    ) {
        eventPublisher.publishEvent(new AuditEventMessage(
                action,
                targetType,
                targetId,
                false,
                reason,
                beforeData,
                null,
                actorUsername
        ));
    }

    public void recordSystem(
            String action,
            String targetType,
            String targetId,
            boolean success,
            String reason,
            Object beforeData,
            Object afterData
    ) {
        recordActor("system", action, targetType, targetId, success, reason, beforeData, afterData);
    }

    private void publishAfterCommit(AuditEventMessage event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            eventPublisher.publishEvent(event);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventPublisher.publishEvent(event);
            }
        });
    }
}
