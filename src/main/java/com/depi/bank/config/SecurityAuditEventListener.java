package com.depi.bank.config;

import com.depi.bank.domain.User;
import com.depi.bank.exception.ErrorCode;
import com.depi.bank.repository.UserRepository;
import com.depi.bank.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;

@Component
public class SecurityAuditEventListener {

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;
    private final int maxFailedAttempts;
    private final int lockoutDurationMinutes;

    public SecurityAuditEventListener(UserRepository userRepository,
                                      AuditLogService auditLogService,
                                      Clock clock,
                                      @Value("${bank.lockout.max-failed-attempts:5}") int maxFailedAttempts,
                                      @Value("${bank.lockout.duration-minutes:15}") int lockoutDurationMinutes) {
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockoutDurationMinutes = lockoutDurationMinutes;
    }

    @EventListener
    @Transactional
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        String email = event.getAuthentication().getName();
        String clientIp = resolveClientIp();
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            user.resetFailedAttempts();
            userRepository.save(user);
            auditLogService.logSuccess(user.getId(), user.getEmail(), "LOGIN_SUCCESS", "AUTH",
                    "Session authenticated", clientIp);
        });
    }

    @EventListener
    @Transactional
    public void onAuthenticationFailure(AuthenticationFailureBadCredentialsEvent event) {
        String email = String.valueOf(event.getAuthentication().getPrincipal()).trim().toLowerCase();
        String clientIp = resolveClientIp();
        userRepository.findByEmailIgnoreCase(email).ifPresentOrElse(user -> {
            boolean justLocked = user.recordFailedLoginAttempt(clock, maxFailedAttempts, lockoutDurationMinutes);
            userRepository.save(user);
            if (justLocked) {
                auditLogService.logFailure(user.getId(), user.getEmail(), "ACCOUNT_LOCKED", "AUTH",
                        ErrorCode.ACCOUNT_LOCKED.name(), "Locked for 15 minutes after 5 consecutive failed attempts", clientIp);
            } else {
                auditLogService.logFailure(user.getId(), user.getEmail(), "LOGIN_FAILURE", "AUTH",
                        ErrorCode.INVALID_CREDENTIALS.name(), "Failed login attempt #" + user.getFailedAttempts(), clientIp);
            }
        }, () -> auditLogService.logFailure(null, email, "LOGIN_FAILURE", "AUTH",
                ErrorCode.INVALID_CREDENTIALS.name(), "Unregistered email login attempt", clientIp));
    }

    @EventListener
    public void onAccountLockedEvent(AuthenticationFailureLockedEvent event) {
        String email = String.valueOf(event.getAuthentication().getPrincipal()).trim().toLowerCase();
        String clientIp = resolveClientIp();
        userRepository.findByEmailIgnoreCase(email).ifPresent(user ->
                auditLogService.logFailure(user.getId(), user.getEmail(), "LOGIN_LOCKED", "AUTH",
                        ErrorCode.ACCOUNT_LOCKED.name(), "Login rejected while account is locked", clientIp)
        );
    }

    private String resolveClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest req = attrs.getRequest();
            return req.getRemoteAddr();
        }
        return "127.0.0.1";
    }
}