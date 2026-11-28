package com.depi.bank.repository;

import com.depi.bank.domain.Transaction;
import com.depi.bank.exception.BankException;
import com.depi.bank.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Page<Transaction> findByAccountNumberAndCreatedAtBetweenOrderByCreatedAtDesc(
            String accountNumber, Instant start, Instant end, Pageable pageable);

    Page<Transaction> findByAccountNumberAndTypeIgnoreCaseAndCreatedAtBetweenOrderByCreatedAtDesc(
            String accountNumber, String type, Instant start, Instant end, Pageable pageable);

    List<Transaction> findByAccountNumberAndCreatedAtBetweenOrderByCreatedAtDesc(
            String accountNumber, Instant start, Instant end);

    List<Transaction> findByAccountNumberAndTypeIgnoreCaseAndCreatedAtBetweenOrderByCreatedAtDesc(
            String accountNumber, String type, Instant start, Instant end);

    long countByCreatedAtBetween(Instant start, Instant end);

    @Query("SELECT t FROM Transaction t WHERE t.type = 'FEE' AND t.createdAt BETWEEN :start AND :end")
    List<Transaction> findFeesCreatedBetween(@Param("start") Instant start, @Param("end") Instant end);

    // Ledger Immutability Enforcement (Append-Only)
    @Override
    default void delete(Transaction entity) {
        throw new BankException(ErrorCode.LEDGER_IMMUTABLE_VIOLATION);
    }

    @Override
    default void deleteById(Long id) {
        throw new BankException(ErrorCode.LEDGER_IMMUTABLE_VIOLATION);
    }

    @Override
    default void deleteAll() {
        throw new BankException(ErrorCode.LEDGER_IMMUTABLE_VIOLATION);
    }
}