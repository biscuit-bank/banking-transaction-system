package com.depi.bank.domain.account;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public LedgerEntry() {
    }

    public static LedgerEntry initialDeposit(UUID accountId, BigDecimal amount, String currency) {
        LedgerEntry entry = new LedgerEntry();
        entry.accountId = accountId;
        entry.type = TransactionType.DEPOSIT;
        entry.amount = amount;
        entry.balanceAfter = amount;
        entry.currency = currency;
        entry.createdAt = Instant.now();
        return entry;
    }

    /**
     * Creates a DEPOSIT entry for a cash deposit, preserving the account's running balance.
     */
    public static LedgerEntry deposit(UUID accountId, BigDecimal amount, BigDecimal balanceAfter,
                                      String currency, Instant createdAt) {
        LedgerEntry entry = new LedgerEntry();
        entry.accountId = accountId;
        entry.type = TransactionType.DEPOSIT;
        entry.amount = amount;
        entry.balanceAfter = balanceAfter;
        entry.currency = currency;
        entry.createdAt = createdAt;
        return entry;
    }

    public static LedgerEntry withdrawal(UUID accountId, BigDecimal amount, BigDecimal balanceAfter, String currency) {
        LedgerEntry entry = new LedgerEntry();
        entry.accountId = accountId;
        entry.type = TransactionType.WITHDRAWAL;
        entry.amount = amount;
        entry.balanceAfter = balanceAfter;
        entry.currency = currency;
        entry.createdAt = Instant.now();
        return entry;
    }

    public static LedgerEntry transferOut(UUID accountId, BigDecimal amount, BigDecimal balanceAfter, String currency) {
        LedgerEntry entry = new LedgerEntry();
        entry.accountId = accountId;
        entry.type = TransactionType.TRANSFER_OUT;
        entry.amount = amount;
        entry.balanceAfter = balanceAfter;
        entry.currency = currency;
        entry.createdAt = Instant.now();
        return entry;
    }

    public static LedgerEntry transferIn(UUID accountId, BigDecimal amount, BigDecimal balanceAfter, String currency) {
        LedgerEntry entry = new LedgerEntry();
        entry.accountId = accountId;
        entry.type = TransactionType.TRANSFER_IN;
        entry.amount = amount;
        entry.balanceAfter = balanceAfter;
        entry.currency = currency;
        entry.createdAt = Instant.now();
        return entry;
    }

    public static LedgerEntry interest(UUID accountId, BigDecimal amount, BigDecimal balanceAfter, String currency) {
        return interest(accountId, amount, balanceAfter, currency, Instant.now());
    }

    /**
     * Creates an INTEREST entry with an explicit timestamp so scheduled jobs can use the
     * injected {@link java.time.Clock} instead of the JVM clock (NFR-05).
     */
    public static LedgerEntry interest(UUID accountId, BigDecimal amount, BigDecimal balanceAfter,
                                       String currency, Instant createdAt) {
        LedgerEntry entry = new LedgerEntry();
        entry.accountId = accountId;
        entry.type = TransactionType.INTEREST;
        entry.amount = amount;
        entry.balanceAfter = balanceAfter;
        entry.currency = currency;
        entry.createdAt = createdAt;
        return entry;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
