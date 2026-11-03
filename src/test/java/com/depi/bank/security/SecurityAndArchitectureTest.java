package com.depi.bank.security;

import com.depi.bank.config.OwnershipGuard;
import com.depi.bank.domain.AuditLog;
import com.depi.bank.domain.IdempotencyKey;
import com.depi.bank.domain.User;
import com.depi.bank.exception.BankException;
import com.depi.bank.exception.ErrorCode;
import com.depi.bank.repository.AuditLogRepository;
import com.depi.bank.repository.UserRepository;
import com.depi.bank.service.AuthService;
import com.depi.bank.service.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAndArchitectureTest {

    @TestConfiguration
    static class MutableClockConfig {
        static final AtomicReference<Instant> CURRENT_TIME =
                new AtomicReference<>(Instant.parse("2026-10-26T10:00:00Z"));

        @Bean
        @Primary
        public Clock mutableClock() {
            return new Clock() {
                @Override
                public ZoneId getZone() {
                    return ZoneId.of("Africa/Cairo");
                }

                @Override
                public Clock withZone(ZoneId zone) {
                    return this;
                }

                @Override
                public Instant instant() {
                    return CURRENT_TIME.get();
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private OwnershipGuard ownershipGuard;

    @Autowired
    private IdempotencyService idempotencyService;

    @BeforeEach
    void resetClock() {
        MutableClockConfig.CURRENT_TIME.set(Instant.parse("2026-10-26T10:00:00Z"));
    }

    @Test
    @DisplayName("TC-REG-01 & TC-SEC-04: Register customer with salted BCrypt hash and reject case-insensitive duplicate email (409)")
    void testRegistrationAndCaseInsensitiveDuplicateEmail() throws Exception {
        String payload1 = """
                {
                  "fullName": "Mona Hassan",
                  "email": "Mona.Hassan@example.com",
                  "phone": "+201001234567",
                  "password": "Secure1Pass"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("mona.hassan@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        User saved = userRepository.findByEmailIgnoreCase("mona.hassan@example.com").orElseThrow();
        assertTrue(saved.getPasswordHash().startsWith("$2a$10$") || saved.getPasswordHash().startsWith("$2b$10$"));

        // Duplicate email in lowercase must return 409 DUPLICATE_EMAIL
        String payloadDuplicate = """
                {
                  "fullName": "Mona Duplicate",
                  "email": "mona.hassan@example.com",
                  "phone": "+201009999999",
                  "password": "Secure1Pass"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadDuplicate))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_EMAIL"));
    }

    @Test
    @DisplayName("TC-AUTH-01: 5 consecutive failed logins lock account for 15 minutes; unlock at 15m 01s using injected Clock")
    void testFiveFailedLoginsLockoutAndClockUnlock() {
        String email = "customer.c@example.com";
        String correctPass = "Customer3Pass";

        // Submit 5 consecutive wrong passwords
        for (int i = 1; i <= 5; i++) {
            BankException ex = assertThrows(BankException.class,
                    () -> authService.authenticate(email, "WrongPass123", "127.0.0.1"));
            if (i < 5) {
                assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getErrorCode());
            } else {
                assertEquals(ErrorCode.ACCOUNT_LOCKED, ex.getErrorCode());
            }
        }

        // Advance Clock by 14 minutes 59 seconds -> Correct password must STILL be refused with generic message
        MutableClockConfig.CURRENT_TIME.updateAndGet(t -> t.plus(Duration.ofMinutes(14).plusSeconds(59)));
        BankException stillLockedEx = assertThrows(BankException.class,
                () -> authService.authenticate(email, correctPass, "127.0.0.1"));
        assertEquals(ErrorCode.ACCOUNT_LOCKED, stillLockedEx.getErrorCode());
        assertEquals("Invalid email or password.", stillLockedEx.getMessage());

        // Advance Clock to 15 minutes 01 second -> Correct password must SUCCEED
        MutableClockConfig.CURRENT_TIME.updateAndGet(t -> t.plusSeconds(2));
        User unlockedUser = authService.authenticate(email, correctPass, "127.0.0.1");
        assertEquals(0, unlockedUser.getFailedAttempts());
        assertNull(unlockedUser.getLockedUntil());
    }

    @Test
    @DisplayName("TC-SEC-01: OwnershipGuard throws HTTP 404 ACCOUNT_NOT_FOUND (never 403) on cross-customer IDOR attempt and audits it")
    void testOwnershipGuardReturnsUniform404AndAudits() {
        Long ownerUserId = 10L;
        Long attackerUserId = 99L;
        String targetAccountNo = "100000007701";

        BankException ex = assertThrows(BankException.class, () ->
                ownershipGuard.verifyAccountOwnership(ownerUserId, attackerUserId,
                        "attacker@example.com", targetAccountNo, "192.168.1.50"));

        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND, ex.getErrorCode());
        assertEquals(404, ex.getErrorCode().getHttpStatus().value());

        // Verify Audit Log entry was written with masked account number ****7701
        boolean auditRecorded = auditLogRepository.findAll().stream()
                .anyMatch(log -> "IDOR_ACCESS_DENIED".equals(log.getActionCode())
                        && "****7701".equals(log.getTargetResource())
                        && "FAILURE".equals(log.getOutcome()));
        assertTrue(auditRecorded, "IDOR attempt must be recorded in Audit Log with masked account number");
    }

    @Test
    @DisplayName("TC-SEC-02 & TC-SEC-03: Separation of duties blocks ADMIN from customer financial endpoints with HTTP 403 and blocks CUSTOMER from /admin/**")
    void testRoleSeparationOfDuties() throws Exception {
        // Admin attempting customer money operation -> HTTP 403 Forbidden (US-25, ADM-07)
        mockMvc.perform(post("/api/v1/transactions/deposit")
                        .with(httpBasic("admin@biscuitbank.eg", "Admin123Pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountNumber\":\"100000000032\",\"amountEgp\":100.00}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"));

        // Customer attempting admin audit log endpoint -> HTTP 403 Forbidden
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .with(httpBasic("customer.a@example.com", "Customer1Pass")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"));

        // Admin accessing admin audit log endpoint -> HTTP 200 OK
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .with(httpBasic("admin@biscuitbank.eg", "Admin123Pass")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-SEC-06 & TC-SEC-08: Verify CSRF protection on Web POSTs and presence of Security Headers (CSP, X-Frame-Options, nosniff)")
    @WithMockUser(username = "customer.a@example.com", roles = "CUSTOMER")
    void testCsrfProtectionAndSecurityHeaders() throws Exception {
        // Web POST to /logout without CSRF token must be rejected (403)
        mockMvc.perform(post("/logout"))
                .andExpect(status().isForbidden());

        // Web POST to /logout with valid CSRF token succeeds and includes Security Headers
        mockMvc.perform(post("/logout").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("Content-Security-Policy"));
    }

    @Test
    @DisplayName("TC-TX-03: Idempotency-Key deduplication returns cached response within 24h and rejects payload mismatch with 409 IDEMPOTENCY_CONFLICT")
    void testIdempotencyKeyStoreAndConflict() {
        String key = "idem-key-001";
        Long userId = 1L;
        String payload = "src=100000000016&dst=100000000024&amt=100000";
        String cachedJson = "{\"reference\":\"TXN-20261026-000001\",\"status\":\"COMPLETED\"}";

        assertTrue(idempotencyService.checkExistingKey(key, userId, payload).isEmpty());

        idempotencyService.saveIdempotencyResponse(key, userId, payload, 201, cachedJson);

        // Same key + same payload within 24h -> returns cached response
        Optional<IdempotencyKey> hit = idempotencyService.checkExistingKey(key, userId, payload);
        assertTrue(hit.isPresent());
        assertEquals(201, hit.get().getResponseStatus());
        assertEquals(cachedJson, hit.get().getResponseBodyJson());

        // Same key + different payload -> throws 409 IDEMPOTENCY_CONFLICT
        BankException conflictEx = assertThrows(BankException.class,
                () -> idempotencyService.checkExistingKey(key, userId, "src=100000000016&dst=999999999999&amt=500000"));
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, conflictEx.getErrorCode());
    }

    @Test
    @DisplayName("TC-LED-01 & TC-AUD-01: AuditLogRepository is strictly append-only and blocks delete attempts")
    void testAuditLogImmutability() {
        AuditLog sample = auditLogRepository.findAll().stream().findFirst().orElseThrow();
        BankException ex1 = assertThrows(BankException.class, () -> auditLogRepository.delete(sample));
        assertEquals(ErrorCode.LEDGER_IMMUTABLE_VIOLATION, ex1.getErrorCode());

        BankException ex2 = assertThrows(BankException.class, () -> auditLogRepository.deleteAll());
        assertEquals(ErrorCode.LEDGER_IMMUTABLE_VIOLATION, ex2.getErrorCode());
    }
}