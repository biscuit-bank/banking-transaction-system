package com.depi.bank.repository;

import com.depi.bank.domain.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {
    Optional<IdempotencyKey> findByIdempotencyKeyAndUserId(String idempotencyKey, Long userId);
}