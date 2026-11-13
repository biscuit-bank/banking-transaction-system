package com.depi.bank.service;

import com.depi.bank.domain.account.Account;
import com.depi.bank.domain.account.AccountStatus;
import com.depi.bank.exception.BankException;
import com.depi.bank.exception.ErrorCode;
import com.depi.bank.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * US-09 / ACC-04: closes an ACTIVE account whose balance is exactly 0.00 EGP.
 *
 * <p>Only ACTIVE accounts may be closed. A FROZEN account is rejected with
 * {@code ACCOUNT_FROZEN}, a CLOSED account with {@code ACCOUNT_CLOSED}, and any non-zero
 * (positive or overdrawn) balance with {@code NON_ZERO_BALANCE_ON_CLOSE}.</p>
 */
@Service
public class AccountClosureService {

    private final AccountRepository accountRepository;
    private final AuditLogService auditLogService;

    public AccountClosureService(AccountRepository accountRepository) {
        this(accountRepository, null);
    }

    @Autowired
    public AccountClosureService(AccountRepository accountRepository, AuditLogService auditLogService) {
        this.accountRepository = accountRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * Closes the account. Ownership is enforced by the caller (controller) when required.
     */
    @Transactional
    public Account closeAccount(String accountNumber) {
        return closeValidated(findAccount(accountNumber));
    }

    /**
     * Closes the account after verifying that it belongs to the requesting customer.
     * A cross-customer attempt is reported as a uniform 404 (SEC-02 / US-04).
     */
    @Transactional
    public Account closeAccount(String accountNumber, UUID ownerCustomerId) {
        Account account = findAccount(accountNumber);
        verifyOwnership(account, ownerCustomerId);
        return closeValidated(account);
    }

    /**
     * Closes the account and records the successful closure in the immutable audit log.
     */
    @Transactional
    public Account closeAccount(String accountNumber, String actorEmail, String clientIp) {
        Account closed = closeValidated(findAccount(accountNumber));
        recordClosure(closed, actorEmail, clientIp);
        return closed;
    }

    /**
     * Closes the account after an ownership check and records the closure in the audit log.
     */
    @Transactional
    public Account closeAccount(String accountNumber, UUID ownerCustomerId,
                                String actorEmail, String clientIp) {
        Account account = findAccount(accountNumber);
        verifyOwnership(account, ownerCustomerId);
        Account closed = closeValidated(account);
        recordClosure(closed, actorEmail, clientIp);
        return closed;
    }

    private Account findAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new BankException(ErrorCode.INVALID_ACCOUNT_NUMBER);
        }
        return accountRepository.findByAccountNumber(accountNumber.trim())
                .orElseThrow(() -> new BankException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private void verifyOwnership(Account account, UUID ownerCustomerId) {
        if (ownerCustomerId == null || !ownerCustomerId.equals(account.getCustomerId())) {
            throw new BankException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
    }

    private Account closeValidated(Account account) {
        if (account.getStatus() == AccountStatus.FROZEN) {
            throw new BankException(ErrorCode.ACCOUNT_FROZEN);
        }
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new BankException(ErrorCode.ACCOUNT_CLOSED);
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BankException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }
        if (account.getBalance() == null
                || account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new BankException(ErrorCode.NON_ZERO_BALANCE_ON_CLOSE);
        }

        account.setStatus(AccountStatus.CLOSED);
        return accountRepository.save(account);
    }

    private void recordClosure(Account account, String actorEmail, String clientIp) {
        if (auditLogService == null) {
            return;
        }
        auditLogService.logSuccess(
                null,
                actorEmail != null ? actorEmail : "SYSTEM",
                "ACCOUNT_CLOSE",
                account.getAccountNumber(),
                "Account closed by owner with zero balance",
                clientIp);
    }
}
