package com.depi.bank.service;

import com.depi.bank.domain.IdempotencyKey;
import com.depi.bank.exception.BankException;
import com.depi.bank.exception.ErrorCode;
import com.depi.bank.repository.IdempotencyKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;
    private final Clock clock;

    public IdempotencyService(IdempotencyKeyRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Checks if an active (non-expired) Idempotency-Key exists for the user.
     * If found with identical request payload hash, returns the cached response.
     * If found with a different payload hash, throws 409 IDEMPOTENCY_CONFLICT (BR-19, TC-TX-03).
     */
    @Transactional(readOnly = true)
    public Optional<IdempotencyKey> checkExistingKey(String key, Long userId, String canonicalPayload) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String payloadHash = sha256Hex(canonicalPayload);
        Optional<IdempotencyKey> existingOpt = repository.findByIdempotencyKeyAndUserId(key.trim(), userId);
        if (existingOpt.isEmpty()) {
            return Optional.empty();
        }
        IdempotencyKey existing = existingOpt.get();
        if (existing.isExpired(Instant.now(clock))) {
            return Optional.empty();
        }
        if (!existing.getRequestHash().equals(payloadHash)) {
            throw new BankException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return Optional.of(existing);
    }

    @Transactional
    public void saveIdempotencyResponse(String key, Long userId, String canonicalPayload, int status, String responseJson) {
        if (key == null || key.isBlank()) {
            return;
        }
        Instant now = Instant.now(clock);
        String payloadHash = sha256Hex(canonicalPayload);
        repository.findByIdempotencyKeyAndUserId(key.trim(), userId).ifPresent(repository::delete);
        IdempotencyKey record = new IdempotencyKey(
                key.trim(),
                userId,
                payloadHash,
                status,
                responseJson,
                now,
                now.plus(24, ChronoUnit.HOURS)
        );
        repository.save(record);
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}