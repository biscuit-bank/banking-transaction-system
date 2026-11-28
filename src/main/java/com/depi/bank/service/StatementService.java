package com.depi.bank.service;

import com.depi.bank.domain.Account;
import com.depi.bank.domain.Transaction;
import com.depi.bank.exception.BankException;
import com.depi.bank.exception.ErrorCode;
import com.depi.bank.repository.CoreAccountRepository;
import com.depi.bank.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

@Service
public class StatementService {

    private final TransactionRepository transactionRepository;
    private final CoreAccountRepository accountRepository;
    private final Clock clock;

    public StatementService(TransactionRepository transactionRepository,
                            CoreAccountRepository accountRepository,
                            Clock clock) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.clock = clock;
    }

    public Page<Transaction> getAccountStatement(String accountNumber,
                                                 String authenticatedEmail,
                                                 LocalDate startDate,
                                                 LocalDate endDate,
                                                 String type,
                                                 int page,
                                                 int size) {
        Account account = verifyOwnership(accountNumber, authenticatedEmail);

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BankException(ErrorCode.VALIDATION_ERROR, "Start date must not be after end date.");
        }

        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);

        Instant startInstant = (startDate != null)
                ? startDate.atStartOfDay(clock.getZone()).toInstant()
                : Instant.EPOCH;
        Instant endInstant = (endDate != null)
                ? endDate.plusDays(1).atStartOfDay(clock.getZone()).toInstant().minusNanos(1)
                : Instant.parse("2100-12-31T23:59:59Z");

        if (type != null && !type.trim().isEmpty()) {
            return transactionRepository.findByAccountNumberAndTypeIgnoreCaseAndCreatedAtBetweenOrderByCreatedAtDesc(
                    account.getAccountNumber(), type.trim(), startInstant, endInstant, pageable);
        }
        return transactionRepository.findByAccountNumberAndCreatedAtBetweenOrderByCreatedAtDesc(
                account.getAccountNumber(), startInstant, endInstant, pageable);
    }

    public Account verifyOwnership(String accountNumber, String authenticatedEmail) {
        if (!AccountFreezeService.isValidLuhnAccountNumber(accountNumber)) {
            throw new BankException(ErrorCode.INVALID_ACCOUNT_NUMBER);
        }
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new BankException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (authenticatedEmail == null || !account.getUser().getEmail().equalsIgnoreCase(authenticatedEmail)) {
            throw new BankException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        return account;
    }
}
