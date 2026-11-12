package com.depi.bank.repository;

import com.depi.bank.domain.account.Account;
import com.depi.bank.domain.account.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    boolean existsByAccountNumber(String accountNumber);

    long countByCustomerIdAndStatusNot(UUID customerId, AccountStatus status);
}
