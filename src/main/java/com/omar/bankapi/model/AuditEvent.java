package com.omar.bankapi.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(nullable = false, length = 80)
    private String targetType;

    @Column(nullable = false, length = 128)
    private String targetId;

    @Column(nullable = false)
    private boolean success;

    @Column(length = 1000)
    private String reason;

    @Column(columnDefinition = "LONGTEXT")
    private String beforeData;

    @Column(columnDefinition = "LONGTEXT")
    private String afterData;

    @Column(length = 100)
    private String actorUsername;

    private Long actorUserId;

    @Column(length = 100)
    private String traceId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
