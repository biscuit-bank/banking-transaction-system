package com.depi.bank.repository;

import com.depi.bank.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByAccountNumber(String accountNumber);
    List<Account> findByStatus(String status);
    List<Account> findByStatusNot(String status);
}
