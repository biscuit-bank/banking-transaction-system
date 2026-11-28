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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;

@Service
public class CheckingWithdrawalService {

    private static final String DEFAULT_CURRENCY = "EGP";
    private static final Set<TransactionType> OUTGOING_TYPES =
            EnumSet.of(TransactionType.WITHDRAWAL, TransactionType.TRANSFER, TransactionType.TRANSFER_OUT, TransactionType.FEE);

    private final AccountRepository accountRepository;
    private final LedgerRepository ledgerRepository;
    private final Clock clock;
    private final BigDecimal maxSingleTxnEgp;
    private final BigDecimal maxDailyDebitEgp;
    private final BigDecimal maxMonthlyDebitEgp;
    private final BigDecimal checkingOverdraftLimitEgp;

    public CheckingWithdrawalService(
            AccountRepository accountRepository,
            LedgerRepository ledgerRepository,
            Clock clock,
            @Value("${bank.limits.max-single-txn-egp:70000.00}") BigDecimal maxSingleTxnEgp,
            @Value("${bank.limits.max-daily-debit-egp:120000.00}") BigDecimal maxDailyDebitEgp,
            @Value("${bank.limits.max-monthly-debit-egp:400000.00}") BigDecimal maxMonthlyDebitEgp,
            @Value("${bank.checking.overdraft-limit-egp:-1000.00}") BigDecimal checkingOverdraftLimitEgp) {
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
        this.clock = clock;
        this.maxSingleTxnEgp = maxSingleTxnEgp;
        this.maxDailyDebitEgp = maxDailyDebitEgp;
        this.maxMonthlyDebitEgp = maxMonthlyDebitEgp;
        this.checkingOverdraftLimitEgp = checkingOverdraftLimitEgp;
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
        if (account.getType() != AccountType.CURRENT && account.getType() != AccountType.CHECKING) {
            throw new BankException(ErrorCode.VALIDATION_ERROR, "Withdrawals from this endpoint require a checking account.");
        }

        validateDailyAndMonthlyLimits(account, normalizedAmount);

        BigDecimal newBalance = account.getBalance().subtract(normalizedAmount).setScale(2, RoundingMode.HALF_EVEN);
        if (newBalance.compareTo(checkingOverdraftLimitEgp) < 0) {
            throw new BankException(ErrorCode.OVERDRAFT_LIMIT_EXCEEDED);
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

    private BigDecimal sumOutgoing(java.util.UUID accountId, Instant fromInclusive, Instant toInclusive) {
        return ledgerRepository.findByAccountIdAndTypeInAndCreatedAtBetween(
                        accountId, OUTGOING_TYPES, fromInclusive, toInclusive)
                .stream()
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
