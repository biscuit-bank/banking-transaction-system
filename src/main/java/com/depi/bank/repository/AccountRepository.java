package com.depi.bank.repository;

import com.depi.bank.domain.account.Account;
import com.depi.bank.domain.account.AccountStatus;
import com.depi.bank.domain.account.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    boolean existsByAccountNumber(String accountNumber);
    Optional<Account> findByAccountNumber(String accountNumber);
    List<Account> findByTypeAndStatus(AccountType type, AccountStatus status);

    long countByCustomerIdAndStatusNot(UUID customerId, AccountStatus status);
}
