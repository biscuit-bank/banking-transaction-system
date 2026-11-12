package com.depi.bank.repository;

import com.depi.bank.domain.account.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface LedgerRepository extends JpaRepository<LedgerEntry, UUID> {
}
