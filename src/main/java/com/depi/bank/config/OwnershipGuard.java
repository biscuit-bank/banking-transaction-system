package com.depi.bank.config;

import com.depi.bank.exception.BankException;
import com.depi.bank.exception.ErrorCode;
import com.depi.bank.service.AuditLogService;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class OwnershipGuard {

    private final AuditLogService auditLogService;

    public OwnershipGuard(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * Verifies that the requested resource owner matches the authenticated customer.
     * On mismatch, logs an IDOR attempt and throws 404 ACCOUNT_NOT_FOUND (never 403) per SEC-02 & US-04.
     */
    public void verifyAccountOwnership(Long resourceOwnerUserId, Long authenticatedUserId,
                                       String authenticatedEmail, String accountNumber, String clientIp) {
        if (resourceOwnerUserId == null || !Objects.equals(resourceOwnerUserId, authenticatedUserId)) {
            auditLogService.logFailure(
                    authenticatedUserId,
                    authenticatedEmail,
                    "IDOR_ACCESS_DENIED",
                    accountNumber,
                    ErrorCode.ACCOUNT_NOT_FOUND.name(),
                    "Cross-customer resource access blocked with uniform 404",
                    clientIp
            );
            throw new BankException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
    }
}