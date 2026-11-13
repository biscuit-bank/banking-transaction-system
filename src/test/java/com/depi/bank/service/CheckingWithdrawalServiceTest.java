package com.depi.bank.service;

import com.depi.bank.domain.account.Account;
import com.depi.bank.domain.account.AccountStatus;
import com.depi.bank.domain.account.AccountType;
import com.depi.bank.domain.account.LedgerEntry;
import com.depi.bank.exception.BankException;
import com.depi.bank.exception.ErrorCode;
import com.depi.bank.repository.AccountRepository;
import com.depi.bank.repository.LedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CheckingWithdrawalServiceTest {

    private AccountRepository accountRepository;
    private LedgerRepository ledgerRepository;
    private CheckingWithdrawalService checkingWithdrawalService;
    private Account checkingAccount;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        ledgerRepository = mock(LedgerRepository.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-13T02:00:00Z"), ZoneId.of("Africa/Cairo"));

        checkingWithdrawalService = new CheckingWithdrawalService(
                accountRepository,
                ledgerRepository,
                fixedClock,
                new BigDecimal("70000.00"),
                new BigDecimal("120000.00"),
                new BigDecimal("400000.00"),
                new BigDecimal("-1000.00")
        );

        checkingAccount = new Account();
        checkingAccount.setAccountNumber("799273987130");
        checkingAccount.setCustomerId(UUID.randomUUID());
        checkingAccount.setType(AccountType.CURRENT);
        checkingAccount.setStatus(AccountStatus.ACTIVE);
        checkingAccount.setBalance(new BigDecimal("-950.00"));
        checkingAccount.setCurrency("EGP");
        checkingAccount.setAnnualInterestRate(BigDecimal.ZERO);

        when(accountRepository.findByAccountNumber("799273987130")).thenReturn(Optional.of(checkingAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerRepository.findByAccountIdAndTypeInAndCreatedAtBetween(any(), any(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void withdraw_toOverdraftBoundary_succeeds() {
        Account updated = checkingWithdrawalService.withdraw("799273987130", new BigDecimal("50.00"));

        assertEquals(0, updated.getBalance().compareTo(new BigDecimal("-1000.00")));
        verify(ledgerRepository).save(any(LedgerEntry.class));
    }

    @Test
    void withdraw_belowOverdraftBoundary_failsWithOverdraftLimitExceeded() {
        BankException ex = assertThrows(BankException.class,
                () -> checkingWithdrawalService.withdraw("799273987130", new BigDecimal("50.01")));

        assertEquals(ErrorCode.OVERDRAFT_LIMIT_EXCEEDED, ex.getErrorCode());
        verify(ledgerRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    void withdraw_fromFrozenAccount_failsWithAccountFrozen() {
        checkingAccount.setStatus(AccountStatus.FROZEN);

        BankException ex = assertThrows(BankException.class,
                () -> checkingWithdrawalService.withdraw("799273987130", new BigDecimal("10.00")));

        assertEquals(ErrorCode.ACCOUNT_FROZEN, ex.getErrorCode());
    }

    @Test
    void withdraw_overPerTransactionLimit_fails() {
        BankException ex = assertThrows(BankException.class,
                () -> checkingWithdrawalService.withdraw("799273987130", new BigDecimal("70000.01")));

        assertEquals(ErrorCode.TXN_LIMIT_EXCEEDED, ex.getErrorCode());
    }

    @Test
    void withdraw_overDailyLimit_fails() {
        LedgerEntry existing = LedgerEntry.withdrawal(
                UUID.randomUUID(),
                new BigDecimal("119900.00"),
                new BigDecimal("0.00"),
                "EGP"
        );
        when(ledgerRepository.findByAccountIdAndTypeInAndCreatedAtBetween(any(), any(), any(), any()))
                .thenReturn(List.of(existing), List.of(existing));

        BankException ex = assertThrows(BankException.class,
                () -> checkingWithdrawalService.withdraw("799273987130", new BigDecimal("200.00")));

        assertEquals(ErrorCode.DAILY_LIMIT_EXCEEDED, ex.getErrorCode());
    }

    @Test
    void withdraw_overMonthlyLimit_fails() {
        LedgerEntry monthly = LedgerEntry.withdrawal(
                UUID.randomUUID(),
                new BigDecimal("399900.00"),
                new BigDecimal("0.00"),
                "EGP"
        );
        when(ledgerRepository.findByAccountIdAndTypeInAndCreatedAtBetween(any(), any(), any(), any()))
                .thenReturn(List.of(), List.of(monthly));

        BankException ex = assertThrows(BankException.class,
                () -> checkingWithdrawalService.withdraw("799273987130", new BigDecimal("200.00")));

        assertEquals(ErrorCode.MONTHLY_LIMIT_EXCEEDED, ex.getErrorCode());
    }
}