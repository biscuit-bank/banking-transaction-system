package com.depi.bank.domain.account;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts", indexes = {
    @Index(name = "idx_account_number", columnList = "accountNumber", unique = true),
    @Index(name = "idx_customer_status", columnList = "customerId, status")
})
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 12)
    private String accountNumber;

    @Column(nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal annualInterestRate;

    @Column(precision = 19, scale = 2)
    private BigDecimal principalAmount;

    private Integer termMonths;

    private Instant maturityDate;

    private Instant maturityInterestCreditedAt;

    /**
     * Calendar month (yyyy-MM) for which monthly savings interest was last credited.
     * Used as a per-account idempotency guard for JOB-01 / BR-08.
     */
    @Column(length = 7)
    private String lastInterestMonth;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public Account() {}

    public UUID getId() { return id; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public AccountType getType() { return type; }
    public void setType(AccountType type) { this.type = type; }
    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getAnnualInterestRate() { return annualInterestRate; }
    public void setAnnualInterestRate(BigDecimal annualInterestRate) { this.annualInterestRate = annualInterestRate; }
    public BigDecimal getPrincipalAmount() { return principalAmount; }
    public void setPrincipalAmount(BigDecimal principalAmount) { this.principalAmount = principalAmount; }
    public Integer getTermMonths() { return termMonths; }
    public void setTermMonths(Integer termMonths) { this.termMonths = termMonths; }
    public Instant getMaturityDate() { return maturityDate; }
    public void setMaturityDate(Instant maturityDate) { this.maturityDate = maturityDate; }
    public Instant getMaturityInterestCreditedAt() { return maturityInterestCreditedAt; }
    public void setMaturityInterestCreditedAt(Instant maturityInterestCreditedAt) {
        this.maturityInterestCreditedAt = maturityInterestCreditedAt;
    }
    public String getLastInterestMonth() { return lastInterestMonth; }
    public void setLastInterestMonth(String lastInterestMonth) { this.lastInterestMonth = lastInterestMonth; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
