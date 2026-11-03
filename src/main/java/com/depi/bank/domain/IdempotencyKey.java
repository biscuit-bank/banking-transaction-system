package com.depi.bank.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "idempotency_keys", indexes = {
        @Index(name = "idx_idem_key_user", columnList = "idempotency_key, user_id", unique = true),
        @Index(name = "idx_idem_expires", columnList = "expires_at")
})
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Column(name = "response_body_json", nullable = false, columnDefinition = "TEXT")
    private String responseBodyJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyKey() {
    }

    public IdempotencyKey(String idempotencyKey, Long userId, String requestHash,
                          int responseStatus, String responseBodyJson, Instant createdAt, Instant expiresAt) {
        this.idempotencyKey = idempotencyKey.trim();
        this.userId = userId;
        this.requestHash = requestHash;
        this.responseStatus = responseStatus;
        this.responseBodyJson = responseBodyJson;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public Long getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Long getUserId() { return userId; }
    public String getRequestHash() { return requestHash; }
    public int getResponseStatus() { return responseStatus; }
    public String getResponseBodyJson() { return responseBodyJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}