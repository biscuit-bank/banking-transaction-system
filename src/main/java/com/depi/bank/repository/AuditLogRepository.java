package com.depi.bank.repository;

import com.depi.bank.domain.AuditLog;
import com.depi.bank.exception.BankException;
import com.depi.bank.exception.ErrorCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    @Override
    default void delete(AuditLog entity) {
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