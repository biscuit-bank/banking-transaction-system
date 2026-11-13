package com.depi.bank.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 128)
    private String key;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false, length = 12)
    private String sourceAccountNumber;

    @Column(nullable = false, length = 12)
    private String destinationAccountNumber;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amountEgp;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal feeEgp;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal sourceBalanceAfter;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal destinationBalanceAfter;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    public IdempotencyKey() {
    }

    public UUID getId() { return id; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public String getSourceAccountNumber() { return sourceAccountNumber; }
    public void setSourceAccountNumber(String sourceAccountNumber) { this.sourceAccountNumber = sourceAccountNumber; }
    public String getDestinationAccountNumber() { return destinationAccountNumber; }
    public void setDestinationAccountNumber(String destinationAccountNumber) { this.destinationAccountNumber = destinationAccountNumber; }
    public BigDecimal getAmountEgp() { return amountEgp; }
    public void setAmountEgp(BigDecimal amountEgp) { this.amountEgp = amountEgp; }
    public BigDecimal getFeeEgp() { return feeEgp; }
    public void setFeeEgp(BigDecimal feeEgp) { this.feeEgp = feeEgp; }
    public BigDecimal getSourceBalanceAfter() { return sourceBalanceAfter; }
    public void setSourceBalanceAfter(BigDecimal sourceBalanceAfter) { this.sourceBalanceAfter = sourceBalanceAfter; }
    public BigDecimal getDestinationBalanceAfter() { return destinationBalanceAfter; }
    public void setDestinationBalanceAfter(BigDecimal destinationBalanceAfter) { this.destinationBalanceAfter = destinationBalanceAfter; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
