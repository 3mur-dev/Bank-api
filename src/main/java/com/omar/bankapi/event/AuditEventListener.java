package com.omar.bankapi.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omar.bankapi.model.AuditEvent;
import com.omar.bankapi.repository.AuditEventRepository;
import com.omar.bankapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AuditEventListener {

    private final AuditEventRepository auditEventRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAuditEvent(AuditEventMessage event) {
        auditEventRepository.save(toEntity(event));
    }

    private AuditEvent toEntity(AuditEventMessage event) {
        AuditEvent auditEvent = new AuditEvent();
        auditEvent.setAction(event.action());
        auditEvent.setTargetType(event.targetType());
        auditEvent.setTargetId(event.targetId());
        auditEvent.setSuccess(event.success());
        auditEvent.setReason(event.reason());
        auditEvent.setBeforeData(asJson(event.beforeData()));
        auditEvent.setAfterData(asJson(event.afterData()));
        auditEvent.setTraceId(MDC.get("traceId"));

        String actorUsername = resolveActorUsername(event.actorUsername());
        auditEvent.setActorUsername(actorUsername);

        if (actorUsername != null && !"system".equals(actorUsername) && !"anonymous".equals(actorUsername)) {
            userRepository.findByUsernameIncludingDeleted(actorUsername)
                    .ifPresent(user -> auditEvent.setActorUserId(user.getId()));
        }

        return auditEvent;
    }

    private String resolveActorUsername(String override) {
        if (override != null && !override.isBlank()) {
            return override;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return auth.getName();
        }

        return "anonymous";
    }

    private String asJson(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }
}
