package com.depi.bank.service;

import com.depi.bank.domain.Account;
import com.depi.bank.domain.AuditLog;
import com.depi.bank.exception.BankException;
import com.depi.bank.exception.ErrorCode;
import com.depi.bank.repository.AccountRepository;
import com.depi.bank.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class AccountFreezeService {

    private final AccountRepository accountRepository;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public AccountFreezeService(AccountRepository accountRepository,
                                AuditLogRepository auditLogRepository,
                                Clock clock) {
        this.accountRepository = accountRepository;
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public Account freezeAccount(String accountNumber, String reason, String adminIdentifier, String clientIp) {
        String validReason = validateReason(reason);
        Account account = getValidAccount(accountNumber);

        if ("CLOSED".equalsIgnoreCase(account.getStatus())) {
            throw new BankException(ErrorCode.ACCOUNT_CLOSED);
        }
        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())) {
            throw new BankException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        account.setStatus("FROZEN");
        Account saved = accountRepository.save(account);

        auditLogRepository.save(new AuditLog(
                null, adminIdentifier, "ACCOUNT_FREEZE", account.getAccountNumber(),
                "SUCCESS", null, validReason, clientIp, Instant.now(clock)
        ));
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public Account unfreezeAccount(String accountNumber, String reason, String adminIdentifier, String clientIp) {
        String validReason = validateReason(reason);
        Account account = getValidAccount(accountNumber);

        if ("CLOSED".equalsIgnoreCase(account.getStatus())) {
            throw new BankException(ErrorCode.ACCOUNT_CLOSED);
        }
        if (!"FROZEN".equalsIgnoreCase(account.getStatus())) {
            throw new BankException(ErrorCode.ACCOUNT_NOT_ACTIVE, "Only FROZEN accounts can be unfrozen.");
        }

        account.setStatus("ACTIVE");
        Account saved = accountRepository.save(account);

        auditLogRepository.save(new AuditLog(
                null, adminIdentifier, "ACCOUNT_UNFREEZE", account.getAccountNumber(),
                "SUCCESS", null, validReason, clientIp, Instant.now(clock)
        ));
        return saved;
    }

    private String validateReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new BankException(ErrorCode.VALIDATION_ERROR, "Reason is mandatory and cannot be empty.");
        }
        String trimmed = reason.trim();
        if (trimmed.length() > 255) {
            throw new BankException(ErrorCode.VALIDATION_ERROR, "Reason must not exceed 255 characters.");
        }
        return trimmed;
    }

    private Account getValidAccount(String accountNumber) {
        if (accountNumber == null || !isValidLuhnAccountNumber(accountNumber)) {
            throw new BankException(ErrorCode.INVALID_ACCOUNT_NUMBER);
        }
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new BankException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    public static boolean isValidLuhnAccountNumber(String number) {
        if (number == null || !number.matches("^\\d{12}$")) {
            return false;
        }
        int sum = 0;
        boolean alternate = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int n = number.charAt(i) - '0';
            if (alternate) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }
}
