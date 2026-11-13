package com.depi.bank.service;

import com.depi.bank.domain.account.Account;
import com.depi.bank.domain.account.AccountStatus;
import com.depi.bank.domain.account.AccountType;
import com.depi.bank.domain.account.LedgerEntry;
import com.depi.bank.domain.account.TransactionType;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class SavingsWithdrawalService {

    private static final String DEFAULT_CURRENCY = "EGP";
    private static final BigDecimal MIN_SAVINGS_BALANCE = new BigDecimal("1000.00");
    private static final Set<TransactionType> OUTGOING_TYPES =
            EnumSet.of(TransactionType.WITHDRAWAL, TransactionType.TRANSFER, TransactionType.TRANSFER_OUT);

    private final AccountRepository accountRepository;
    private final LedgerRepository ledgerRepository;
    private final Clock clock;
    private final BigDecimal maxSingleTxnEgp;
    private final BigDecimal maxDailyDebitEgp;
    private final BigDecimal maxMonthlyDebitEgp;

    @Autowired
    public SavingsWithdrawalService(AccountRepository accountRepository, LedgerRepository ledgerRepository) {
        this(accountRepository, ledgerRepository, java.time.Clock.systemUTC(),
                new BigDecimal("70000.00"), new BigDecimal("120000.00"), new BigDecimal("400000.00"));
    }

    public SavingsWithdrawalService(AccountRepository accountRepository, LedgerRepository ledgerRepository, Clock clock) {
        this(accountRepository, ledgerRepository, clock,
                new BigDecimal("70000.00"), new BigDecimal("120000.00"), new BigDecimal("400000.00"));
    }

    public SavingsWithdrawalService(AccountRepository accountRepository, LedgerRepository ledgerRepository,
                                   Clock clock, BigDecimal maxSingleTxnEgp, BigDecimal maxDailyDebitEgp,
                                   BigDecimal maxMonthlyDebitEgp) {
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
        this.clock = clock;
        this.maxSingleTxnEgp = maxSingleTxnEgp;
        this.maxDailyDebitEgp = maxDailyDebitEgp;
        this.maxMonthlyDebitEgp = maxMonthlyDebitEgp;
    }

    public SavingsWithdrawalService(AccountRepository accountRepository, LedgerRepository ledgerRepository,
                                   BigDecimal maxSingleTxnEgp, BigDecimal maxDailyDebitEgp,
                                   BigDecimal maxMonthlyDebitEgp) {
        this(accountRepository, ledgerRepository, java.time.Clock.systemUTC(),
                maxSingleTxnEgp, maxDailyDebitEgp, maxMonthlyDebitEgp);
    }

    public SavingsWithdrawalService(AccountRepository accountRepository, LedgerRepository ledgerRepository,
                                   Clock clock, BigDecimal maxSingleTxnEgp, BigDecimal maxDailyDebitEgp,
                                   BigDecimal maxMonthlyDebitEgp, BigDecimal ignoredOverdraftLimit) {
        this(accountRepository, ledgerRepository, clock, maxSingleTxnEgp, maxDailyDebitEgp, maxMonthlyDebitEgp);
    }

    public SavingsWithdrawalService(AccountRepository accountRepository, LedgerRepository ledgerRepository,
                                   BigDecimal maxSingleTxnEgp, BigDecimal maxDailyDebitEgp,
                                   BigDecimal maxMonthlyDebitEgp, BigDecimal ignoredOverdraftLimit) {
        this(accountRepository, ledgerRepository, java.time.Clock.systemUTC(),
                maxSingleTxnEgp, maxDailyDebitEgp, maxMonthlyDebitEgp);
    }

    @Transactional
    public Account withdraw(String accountNumber, BigDecimal amountEgp) {
        if (amountEgp == null || amountEgp.scale() > 2 || amountEgp.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankException(ErrorCode.INVALID_AMOUNT);
        }

        BigDecimal normalizedAmount = amountEgp.setScale(2, RoundingMode.HALF_EVEN);
        if (normalizedAmount.compareTo(maxSingleTxnEgp) > 0) {
            throw new BankException(ErrorCode.TXN_LIMIT_EXCEEDED);
        }

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
        if (account.getType() != AccountType.SAVINGS) {
            throw new BankException(ErrorCode.VALIDATION_ERROR, "Withdrawals from this endpoint require a savings account.");
        }

        validateDailyAndMonthlyLimits(account, normalizedAmount);

        BigDecimal newBalance = account.getBalance().subtract(normalizedAmount).setScale(2, RoundingMode.HALF_EVEN);
        if (newBalance.compareTo(MIN_SAVINGS_BALANCE) < 0) {
            throw new BankException(ErrorCode.MIN_BALANCE_VIOLATION);
        }

        account.setBalance(newBalance);
        Account savedAccount = accountRepository.save(account);
        ledgerRepository.save(LedgerEntry.withdrawal(
                savedAccount.getId(),
                normalizedAmount,
                newBalance,
                DEFAULT_CURRENCY
        ));

        return savedAccount;
    }

    private void validateDailyAndMonthlyLimits(Account account, BigDecimal amountEgp) {
        Instant now = Instant.now(clock);
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.ofInstant(now, zone);

        Instant dayStart = today.atStartOfDay(zone).toInstant();
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant();

        BigDecimal todayOutgoing = sumOutgoing(account.getId(), dayStart, now);
        if (todayOutgoing.add(amountEgp).compareTo(maxDailyDebitEgp) > 0) {
            throw new BankException(ErrorCode.DAILY_LIMIT_EXCEEDED);
        }

        BigDecimal monthOutgoing = sumOutgoing(account.getId(), monthStart, now);
        if (monthOutgoing.add(amountEgp).compareTo(maxMonthlyDebitEgp) > 0) {
            throw new BankException(ErrorCode.MONTHLY_LIMIT_EXCEEDED);
        }
    }

    private BigDecimal sumOutgoing(UUID accountId, Instant fromInclusive, Instant toInclusive) {
        return ledgerRepository.findByAccountIdAndTypeInAndCreatedAtBetween(
                        accountId, OUTGOING_TYPES, fromInclusive, toInclusive)
                .stream()
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
