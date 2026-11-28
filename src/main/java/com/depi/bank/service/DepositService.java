package com.depi.bank.service;

import com.depi.bank.domain.account.Account;
import com.depi.bank.domain.account.AccountStatus;
import com.depi.bank.domain.account.AccountType;
import com.depi.bank.domain.account.LedgerEntry;
import com.depi.bank.exception.BankException;
import com.depi.bank.exception.ErrorCode;
import com.depi.bank.repository.AccountRepository;
import com.depi.bank.repository.LedgerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;

/**
 * US-11: cash deposit into an ACTIVE Savings or Checking account.
 *
 * <p>The maximum deposit per transaction is 70,000.00 EGP. Fixed Deposit accounts cannot
 * receive additional deposits. Each successful deposit writes exactly one immutable
 * {@code DEPOSIT} ledger row and updates the account balance in the same transaction.</p>
 */
@Service
public class DepositService {

    private static final BigDecimal MAX_DEPOSIT_EGP = new BigDecimal("70000.00");
    private static final String DEFAULT_CURRENCY = "EGP";

    private final AccountRepository accountRepository;
    private final LedgerRepository ledgerRepository;
    private final Clock clock;

    public DepositService(AccountRepository accountRepository, LedgerRepository ledgerRepository, Clock clock) {
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
        this.clock = clock;
    }

    @Transactional
    public Account deposit(String accountNumber, BigDecimal amountEgp) {
        if (amountEgp == null || amountEgp.scale() > 2 || amountEgp.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankException(ErrorCode.INVALID_AMOUNT);
        }

        BigDecimal amount = amountEgp.setScale(2, RoundingMode.HALF_EVEN);
        if (amount.compareTo(MAX_DEPOSIT_EGP) > 0) {
            throw new BankException(ErrorCode.TXN_LIMIT_EXCEEDED);
        }

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new BankException(ErrorCode.ACCOUNT_NOT_FOUND));

        validateDepositable(account);

        BigDecimal newBalance = account.getBalance().add(amount).setScale(2, RoundingMode.HALF_EVEN);
        account.setBalance(newBalance);
        Account savedAccount = accountRepository.save(account);

        Instant postedAt = Instant.now(clock);
        ledgerRepository.save(LedgerEntry.deposit(
                savedAccount.getId(),
                amount,
                newBalance,
                resolveCurrency(savedAccount),
                postedAt));

        return savedAccount;
    }

    private void validateDepositable(Account account) {
        if (account.getStatus() == AccountStatus.FROZEN) {
            throw new BankException(ErrorCode.ACCOUNT_FROZEN);
        }
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new BankException(ErrorCode.ACCOUNT_CLOSED);
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BankException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }
        if (account.getType() == AccountType.FIXED_DEPOSIT) {
            throw new BankException(ErrorCode.FD_DEPOSIT_NOT_ALLOWED);
        }
        if (account.getType() != AccountType.SAVINGS
                && account.getType() != AccountType.CURRENT
                && account.getType() != AccountType.CHECKING) {
            throw new BankException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }
    }

    private String resolveCurrency(Account account) {
        return account.getCurrency() != null ? account.getCurrency() : DEFAULT_CURRENCY;
    }
}
