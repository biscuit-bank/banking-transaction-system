package com.depi.bank.service;

import com.depi.bank.domain.account.Account;
import com.depi.bank.domain.account.AccountStatus;
import com.depi.bank.domain.account.AccountType;
import com.depi.bank.domain.account.LedgerEntry;
import com.depi.bank.exception.BankException;
import com.depi.bank.exception.ErrorCode;
import com.depi.bank.repository.AccountRepository;
import com.depi.bank.repository.LedgerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

@Service
public class FixedDepositWithdrawalService {

    private static final String DEFAULT_CURRENCY = "EGP";

    private final AccountRepository accountRepository;
    private final LedgerRepository ledgerRepository;
    private final Clock clock;

    @Autowired
    public FixedDepositWithdrawalService(AccountRepository accountRepository, LedgerRepository ledgerRepository) {
        this(accountRepository, ledgerRepository, java.time.Clock.systemUTC());
    }

    public FixedDepositWithdrawalService(AccountRepository accountRepository, LedgerRepository ledgerRepository, Clock clock) {
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
        this.clock = clock;
    }

    @Transactional
    public Account withdraw(String accountNumber, BigDecimal amountEgp) {
        if (amountEgp == null || amountEgp.scale() > 2 || amountEgp.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankException(ErrorCode.INVALID_AMOUNT);
        }

        BigDecimal normalizedAmount = amountEgp.setScale(2, RoundingMode.HALF_EVEN);

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new BankException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (account.getStatus() == AccountStatus.FROZEN) {
            throw new BankException(ErrorCode.ACCOUNT_FROZEN);
        }
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new BankException(ErrorCode.ACCOUNT_CLOSED);
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BankException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }
        if (account.getType() != AccountType.FIXED_DEPOSIT) {
            throw new BankException(ErrorCode.VALIDATION_ERROR, "Withdrawals from this endpoint require a fixed deposit account.");
        }

        Instant now = Instant.now(clock);
        if (account.getMaturityDate() == null || now.isBefore(account.getMaturityDate())) {
            throw new BankException(ErrorCode.FD_NOT_MATURE);
        }
        if (account.getMaturityInterestCreditedAt() == null) {
            throw new BankException(ErrorCode.FD_NOT_MATURE);
        }

        if (normalizedAmount.compareTo(account.getBalance()) != 0) {
            throw new BankException(ErrorCode.FD_PARTIAL_WITHDRAWAL_NOT_ALLOWED);
        }

        BigDecimal newBalance = account.getBalance().subtract(normalizedAmount).setScale(2, RoundingMode.HALF_EVEN);
        account.setBalance(newBalance);
        Account savedAccount = accountRepository.save(account);
        ledgerRepository.save(LedgerEntry.withdrawal(
                savedAccount.getId(),
                normalizedAmount,
                newBalance,
                account.getCurrency() != null ? account.getCurrency() : DEFAULT_CURRENCY
        ));

        return savedAccount;
    }

    public static boolean isMature(Account account, Clock clock) {
        Instant now = Instant.now(clock);
        return account.getMaturityDate() != null
                && !now.isBefore(account.getMaturityDate())
                && account.getMaturityInterestCreditedAt() != null;
    }
}
