package com.depi.bank.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_txn_account_time", columnList = "account_number, created_at"),
        @Index(name = "idx_txn_created_at", columnList = "created_at")
})
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number", nullable = false, length = 12)
    private String accountNumber;

    @Column(name = "reference", nullable = false, length = 30)
    private String reference;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Convert(converter = PiasterAttributeConverter.class)
    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Convert(converter = PiasterAttributeConverter.class)
    @Column(name = "balance_after", nullable = false)
    private BigDecimal balanceAfter;

    @Column(name = "description", length = 100)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Transaction() {}

    public Transaction(String accountNumber, String reference, String type, BigDecimal amount,
                       BigDecimal balanceAfter, String description, Instant createdAt) {
        this.accountNumber = accountNumber;
        this.reference = reference;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.description = description;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getAccountNumber() { return accountNumber; }
    public String getReference() { return reference; }
    public String getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
}