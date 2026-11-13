package com.depi.bank.repository;

import com.depi.bank.domain.account.LedgerEntry;
import com.depi.bank.domain.account.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LedgerRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findByAccountIdAndTypeInAndCreatedAtBetween(
            UUID accountId, Collection<TransactionType> types, Instant fromInclusive, Instant toInclusive);
    boolean existsByAccountIdAndTypeAndCreatedAtBetween(
            UUID accountId, TransactionType type, Instant fromInclusive, Instant toInclusive);
    boolean existsByAccountIdAndType(UUID accountId, TransactionType type);
}
