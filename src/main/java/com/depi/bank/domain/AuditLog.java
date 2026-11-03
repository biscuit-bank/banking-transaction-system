package com.depi.bank.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_aud_created_action", columnList = "created_at, action_code")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_identifier", nullable = false, length = 100)
    private String actorIdentifier;

    @Column(name = "action_code", nullable = false, length = 40)
    private String actionCode;

    @Column(name = "target_resource", length = 60)
    private String targetResource;

    @Column(name = "outcome", nullable = false, length = 15)
    private String outcome;

    @Column(name = "error_code", length = 40)
    private String errorCode;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "client_ip", nullable = false, length = 45)
    private String clientIp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    public AuditLog(Long actorUserId, String actorIdentifier, String actionCode, String targetResource,
                    String outcome, String errorCode, String reason, String clientIp, Instant createdAt) {
        this.actorUserId = actorUserId;
        this.actorIdentifier = actorIdentifier;
        this.actionCode = actionCode;
        this.targetResource = maskIfAccountNumber(targetResource);
        this.outcome = outcome;
        this.errorCode = errorCode;
        this.reason = reason;
        this.clientIp = (clientIp != null && !clientIp.isBlank()) ? clientIp : "127.0.0.1";
        this.createdAt = createdAt;
    }

    public static String maskIfAccountNumber(String resource) {
        if (resource != null && resource.matches("^\\d{12}$")) {
            return "****" + resource.substring(8);
        }
        return resource;
    }

    public Long getId() { return id; }
    public Long getActorUserId() { return actorUserId; }
    public String getActorIdentifier() { return actorIdentifier; }
    public String getActionCode() { return actionCode; }
    public String getTargetResource() { return targetResource; }
    public String getOutcome() { return outcome; }
    public String getErrorCode() { return errorCode; }
    public String getReason() { return reason; }
    public String getClientIp() { return clientIp; }
    public Instant getCreatedAt() { return createdAt; }
}