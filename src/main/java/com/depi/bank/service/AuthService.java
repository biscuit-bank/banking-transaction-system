package com.depi.bank.service;

import com.depi.bank.domain.Role;
import com.depi.bank.domain.User;
import com.depi.bank.exception.BankException;
import com.depi.bank.exception.ErrorCode;
import com.depi.bank.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern PASSWORD_POLICY = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,64}$");
    private static final Pattern NAME_POLICY = Pattern.compile("^[A-Za-z\\s\\-']{2,50}$");
    private static final Pattern PHONE_POLICY = Pattern.compile("^\\+?\\d{10,15}$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final Clock clock;
    private final int maxFailedAttempts;
    private final int lockoutDurationMinutes;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuditLogService auditLogService,
                       Clock clock,
                       @Value("${bank.lockout.max-failed-attempts:5}") int maxFailedAttempts,
                       @Value("${bank.lockout.duration-minutes:15}") int lockoutDurationMinutes) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.clock = clock;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockoutDurationMinutes = lockoutDurationMinutes;
    }

    @Transactional(noRollbackFor = BankException.class)
    public User registerCustomer(String fullName, String email, String phone, String rawPassword, String clientIp) {
        if (fullName == null || !NAME_POLICY.matcher(fullName.trim()).matches()) {
            throw new BankException(ErrorCode.VALIDATION_ERROR, "Full name must be 2 to 50 letters without digits.");
        }
        if (email == null || !email.contains("@") || email.trim().length() > 100) {
            throw new BankException(ErrorCode.VALIDATION_ERROR, "Please provide a valid email address (max 100 characters).");
        }
        if (phone != null && !phone.isBlank() && !PHONE_POLICY.matcher(phone.trim()).matches()) {
            throw new BankException(ErrorCode.VALIDATION_ERROR, "Phone number must be 10 to 15 digits.");
        }
        if (rawPassword == null || !PASSWORD_POLICY.matcher(rawPassword).matches()) {
            throw new BankException(ErrorCode.VALIDATION_ERROR,
                    "Password must be 8 to 64 characters with at least one uppercase letter, one lowercase letter, and one digit.");
        }

        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            auditLogService.logFailure(null, normalizedEmail, "REGISTER", normalizedEmail,
                    ErrorCode.DUPLICATE_EMAIL.name(), "Duplicate email registration attempt", clientIp);
            throw new BankException(ErrorCode.DUPLICATE_EMAIL);
        }

        String hashed = passwordEncoder.encode(rawPassword);
        User saved = userRepository.save(new User(fullName, normalizedEmail, phone, hashed, Role.CUSTOMER, Instant.now(clock)));
        auditLogService.logSuccess(saved.getId(), saved.getEmail(), "REGISTER", saved.getEmail(), "Customer registered", clientIp);
        return saved;
    }

    @Transactional(noRollbackFor = BankException.class)
    public User authenticate(String email, String rawPassword, String clientIp) {
        if (email == null || rawPassword == null) {
            throw new BankException(ErrorCode.INVALID_CREDENTIALS);
        }
        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> {
                    auditLogService.logFailure(null, email.trim().toLowerCase(), "LOGIN_FAILURE", "AUTH",
                            ErrorCode.INVALID_CREDENTIALS.name(), "Unregistered email", clientIp);
                    return new BankException(ErrorCode.INVALID_CREDENTIALS);
                });

        if (user.isAccountLocked(clock)) {
            auditLogService.logFailure(user.getId(), user.getEmail(), "LOGIN_LOCKED", "AUTH",
                    ErrorCode.ACCOUNT_LOCKED.name(), "Attempt during active 15-minute lockout", clientIp);
            throw new BankException(ErrorCode.ACCOUNT_LOCKED);
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            boolean justLocked = user.recordFailedLoginAttempt(clock, maxFailedAttempts, lockoutDurationMinutes);
            userRepository.save(user);
            if (justLocked) {
                auditLogService.logFailure(user.getId(), user.getEmail(), "ACCOUNT_LOCKED", "AUTH",
                        ErrorCode.ACCOUNT_LOCKED.name(), "Locked for 15 minutes after 5 failed attempts", clientIp);
                throw new BankException(ErrorCode.ACCOUNT_LOCKED);
            }
            auditLogService.logFailure(user.getId(), user.getEmail(), "LOGIN_FAILURE", "AUTH",
                    ErrorCode.INVALID_CREDENTIALS.name(), "Wrong password attempt #" + user.getFailedAttempts(), clientIp);
            throw new BankException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.resetFailedAttempts();
        userRepository.save(user);
        auditLogService.logSuccess(user.getId(), user.getEmail(), "LOGIN_SUCCESS", "AUTH", "Authenticated", clientIp);
        return user;
    }
}