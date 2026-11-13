package com.depi.bank.service;

import com.depi.bank.domain.LuhnAccountNumberService;
import com.depi.bank.domain.account.*;
import com.depi.bank.exception.BankException;
import com.depi.bank.exception.ErrorCode;
import com.depi.bank.repository.AccountRepository;
import com.depi.bank.repository.LedgerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

@Service
public class AccountOpeningService {

    private static final BigDecimal MIN_SAVINGS_DEPOSIT = new BigDecimal("1000.00");
    private static final BigDecimal MAX_CHECKING_OPENING = new BigDecimal("100000.00");
    private static final BigDecimal SAVINGS_ANNUAL_RATE = new BigDecimal("0.1700");
    private static final BigDecimal CHECKING_ANNUAL_RATE = BigDecimal.ZERO;
    private static final int MAX_NON_CLOSED_ACCOUNTS = 5;
    private static final String DEFAULT_CURRENCY = "EGP";

    private final AccountRepository accountRepository;
    private final LedgerRepository ledgerRepository;
    private final LuhnAccountNumberService luhnService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AccountOpeningService(
            AccountRepository accountRepository,
            LedgerRepository ledgerRepository,
            LuhnAccountNumberService luhnService) {
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
        this.luhnService = luhnService;
    }

    @Transactional
    public Account openSavingsAccount(UUID customerId, BigDecimal initialDeposit) {
        validateAmountScale(initialDeposit);
        if (initialDeposit == null || initialDeposit.compareTo(MIN_SAVINGS_DEPOSIT) < 0) {
            throw new BankException(ErrorCode.MIN_BALANCE_VIOLATION);
        }

        return openAccount(customerId, AccountType.SAVINGS, initialDeposit, SAVINGS_ANNUAL_RATE, true);
    }

    @Transactional
    public Account openCheckingAccount(UUID customerId, BigDecimal initialDeposit) {
        validateAmountScale(initialDeposit);
        if (initialDeposit == null
                || initialDeposit.compareTo(BigDecimal.ZERO) < 0
                || initialDeposit.compareTo(MAX_CHECKING_OPENING) > 0) {
            throw new BankException(ErrorCode.INVALID_OPENING_AMOUNT);
        }

        return openAccount(customerId, AccountType.CURRENT, initialDeposit, CHECKING_ANNUAL_RATE,
                initialDeposit.compareTo(BigDecimal.ZERO) > 0);
    }

    private void validateAmountScale(BigDecimal amount) {
        if (amount != null && amount.scale() > 2) {
            throw new BankException(ErrorCode.INVALID_AMOUNT);
        }
    }

    private Account openAccount(UUID customerId, AccountType type, BigDecimal initialDeposit,
                               BigDecimal annualInterestRate, boolean createInitialDepositLedger) {
        long openAccountCount = accountRepository.countByCustomerIdAndStatusNot(
                customerId, AccountStatus.CLOSED);
        if (openAccountCount >= MAX_NON_CLOSED_ACCOUNTS) {
            throw new BankException(ErrorCode.MAX_ACCOUNTS_EXCEEDED);
        }

        String uniqueAccountNumber = generateUniqueAccountNumber();
        BigDecimal normalizedDeposit = initialDeposit.setScale(2, RoundingMode.HALF_EVEN);

        Account account = new Account();
        account.setAccountNumber(uniqueAccountNumber);
        account.setCustomerId(customerId);
        account.setType(type);
        account.setStatus(AccountStatus.ACTIVE);
        account.setBalance(normalizedDeposit);
        account.setCurrency(DEFAULT_CURRENCY);
        account.setAnnualInterestRate(annualInterestRate);
        account.setCreatedAt(Instant.now());

        Account savedAccount = accountRepository.save(account);

        if (createInitialDepositLedger) {
            LedgerEntry initialLedger = LedgerEntry.initialDeposit(
                    savedAccount.getId(),
                    normalizedDeposit,
                    DEFAULT_CURRENCY);
            ledgerRepository.save(initialLedger);
        }

        return savedAccount;
    }

    private String generateUniqueAccountNumber() {
        for (int attempt = 0; attempt < 10; attempt++) {
            long seed = secureRandom.nextLong();
            String candidate = luhnService.generateDeterministic(seed);
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique Luhn account number after 10 attempts.");
    }
}
