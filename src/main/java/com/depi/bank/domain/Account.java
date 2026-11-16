package com.depi.bank.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "accounts", indexes = {
        @Index(name = "idx_accounts_number", columnList = "account_number", unique = true),
        @Index(name = "idx_accounts_user", columnList = "user_id")
})
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number", nullable = false, unique = true, length = 12)
    private String accountNumber;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "account_type", nullable = false, length = 20)
    private String accountType;

    @Convert(converter = PiasterAttributeConverter.class)
    @Column(name = "balance", nullable = false)
    private BigDecimal balance;

    @Column(name = "annual_interest_rate", precision = 5, scale = 4)
    private BigDecimal annualInterestRate;

    @Column(name = "term_months")
    private Integer termMonths;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(name = "last_interest_posted_month", length = 7)
    private String lastInterestPostedMonth;

    @Column(name = "interest_credited", nullable = false)
    private boolean interestCredited = false;

    @Column(name = "status", nullable = false, length = 15)
    private String status;

    protected Account() {}

    public Account(String accountNumber, User user, String accountType, BigDecimal balance,
                   BigDecimal annualInterestRate, Integer termMonths, LocalDate maturityDate) {
        this.accountNumber = accountNumber;
        this.user = user;
        this.accountType = accountType;
        this.balance = balance;
        this.annualInterestRate = annualInterestRate;
        this.termMonths = termMonths;
        this.maturityDate = maturityDate;
        this.status = "ACTIVE";
        this.interestCredited = false;
    }

    public Long getId() { return id; }
    public String getAccountNumber() { return accountNumber; }
    public User getUser() { return user; }
    public String getAccountType() { return accountType; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public BigDecimal getAnnualInterestRate() { return annualInterestRate; }
    public Integer getTermMonths() { return termMonths; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public String getLastInterestPostedMonth() { return lastInterestPostedMonth; }
    public void setLastInterestPostedMonth(String lastInterestPostedMonth) { this.lastInterestPostedMonth = lastInterestPostedMonth; }
    public boolean isInterestCredited() { return interestCredited; }
    public void setInterestCredited(boolean interestCredited) { this.interestCredited = interestCredited; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}