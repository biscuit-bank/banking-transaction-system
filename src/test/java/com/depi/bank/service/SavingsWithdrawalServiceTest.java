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
import org.mockito.ArgumentCaptor;

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

class SavingsWithdrawalServiceTest {

    private AccountRepository accountRepository;
    private LedgerRepository ledgerRepository;
    private SavingsWithdrawalService service;
    private Account account;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        ledgerRepository = mock(LedgerRepository.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-13T02:00:00Z"), ZoneId.of("Africa/Cairo"));
        service = new SavingsWithdrawalService(accountRepository, ledgerRepository, fixedClock,
                new BigDecimal("70000.00"), new BigDecimal("120000.00"), new BigDecimal("400000.00"));

        account = new Account();
        account.setAccountNumber("799273987130");
        account.setCustomerId(UUID.randomUUID());
        account.setType(AccountType.SAVINGS);
        account.setStatus(AccountStatus.ACTIVE);
        account.setBalance(new BigDecimal("1500.00"));
        account.setCurrency("EGP");

        when(accountRepository.findByAccountNumber("799273987130")).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));
        when(ledgerRepository.save(any(LedgerEntry.class))).thenAnswer(i -> i.getArgument(0));
        when(ledgerRepository.findByAccountIdAndTypeInAndCreatedAtBetween(any(), any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void withdraw_minBalanceBoundary_succeeds() {
        Account updated = service.withdraw("799273987130", new BigDecimal("500.00"));
        assertEquals(0, updated.getBalance().compareTo(new BigDecimal("1000.00")));
        verify(ledgerRepository).save(any(LedgerEntry.class));
    }

    @Test
    void withdraw_belowMinBalance_fails() {
        BankException ex = assertThrows(BankException.class,
                () -> service.withdraw("799273987130", new BigDecimal("500.01")));
        assertEquals(ErrorCode.MIN_BALANCE_VIOLATION, ex.getErrorCode());
        verify(ledgerRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    void withdraw_overPerTransactionLimit_fails() {
        BankException ex = assertThrows(BankException.class,
                () -> service.withdraw("799273987130", new BigDecimal("70000.01")));
        assertEquals(ErrorCode.TXN_LIMIT_EXCEEDED, ex.getErrorCode());
    }

    @Test
    void withdraw_overDailyLimit_fails() {
        LedgerEntry existing = LedgerEntry.withdrawal(UUID.randomUUID(), new BigDecimal("119900.00"),
                new BigDecimal("0.00"), "EGP");
        when(ledgerRepository.findByAccountIdAndTypeInAndCreatedAtBetween(any(), any(), any(), any()))
                .thenReturn(List.of(existing), List.of(existing));

        BankException ex = assertThrows(BankException.class,
                () -> service.withdraw("799273987130", new BigDecimal("200.00")));
        assertEquals(ErrorCode.DAILY_LIMIT_EXCEEDED, ex.getErrorCode());
    }

    @Test
    void withdraw_overMonthlyLimit_fails() {
        LedgerEntry monthly = LedgerEntry.withdrawal(UUID.randomUUID(), new BigDecimal("399900.00"),
                new BigDecimal("0.00"), "EGP");
        when(ledgerRepository.findByAccountIdAndTypeInAndCreatedAtBetween(any(), any(), any(), any()))
                .thenReturn(List.of(), List.of(monthly));

        BankException ex = assertThrows(BankException.class,
                () -> service.withdraw("799273987130", new BigDecimal("200.00")));
        assertEquals(ErrorCode.MONTHLY_LIMIT_EXCEEDED, ex.getErrorCode());
    }

    @Test
    void withdraw_fromFrozen_fails() {
        account.setStatus(AccountStatus.FROZEN);
        BankException ex = assertThrows(BankException.class,
                () -> service.withdraw("799273987130", new BigDecimal("10.00")));
        assertEquals(ErrorCode.ACCOUNT_FROZEN, ex.getErrorCode());
    }
}
