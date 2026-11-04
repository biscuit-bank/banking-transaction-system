package com.depi.bank.service;

import com.depi.bank.domain.AuditLog;
import com.depi.bank.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public AuditLogService(AuditLogRepository auditLogRepository, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    @Transactional
    public void logSuccess(Long actorId, String actorEmail, String actionCode, String target, String reason, String clientIp) {
        AuditLog entry = new AuditLog(
                actorId,
                actorEmail != null ? actorEmail : "SYSTEM",
                actionCode,
                target,
                "SUCCESS",
                null,
                reason,
                clientIp,
                Instant.now(clock)
        );
        auditLogRepository.save(entry);
    }

    @Transactional
    public void logFailure(Long actorId, String actorEmail, String actionCode, String target, String errorCode, String reason, String clientIp) {
        AuditLog entry = new AuditLog(
                actorId,
                actorEmail != null ? actorEmail : "ANONYMOUS",
                actionCode,
                target,
                "FAILURE",
                errorCode,
                reason,
                clientIp,
                Instant.now(clock)
        );
        auditLogRepository.save(entry);
    }
}