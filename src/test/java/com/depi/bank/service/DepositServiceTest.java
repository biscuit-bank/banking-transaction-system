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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DepositServiceTest {

    private AccountRepository accountRepository;
    private LedgerRepository ledgerRepository;
    private DepositService depositService;
    private Account account;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        ledgerRepository = mock(LedgerRepository.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneId.of("Africa/Cairo"));
        depositService = new DepositService(accountRepository, ledgerRepository, fixedClock);

        account = new Account();
        account.setAccountNumber("799273987138");
        account.setCustomerId(UUID.randomUUID());
        account.setType(AccountType.SAVINGS);
        account.setStatus(AccountStatus.ACTIVE);
        account.setBalance(new BigDecimal("1000.00"));
        account.setCurrency("EGP");
        account.setAnnualInterestRate(new BigDecimal("0.1700"));

        when(accountRepository.findByAccountNumber("799273987138")).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void depositIntoActiveSavingsAccount_increasesBalanceAndWritesDepositLedgerEntry() {
        Account updated = depositService.deposit("799273987138", new BigDecimal("250.50"));

        assertEquals(0, updated.getBalance().compareTo(new BigDecimal("1250.50")));

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository).save(captor.capture());
        LedgerEntry entry = captor.getValue();
        assertEquals(TransactionType.DEPOSIT, entry.getType());
        assertEquals(new BigDecimal("250.50"), entry.getAmount());
        assertEquals(new BigDecimal("1250.50"), entry.getBalanceAfter());
        assertEquals("EGP", entry.getCurrency());
    }

    @Test
    void depositAtMaximumPerTransactionLimit_succeeds() {
        Account updated = depositService.deposit("799273987138", new BigDecimal("70000.00"));

        assertEquals(0, updated.getBalance().compareTo(new BigDecimal("71000.00")));
    }

    @Test
    void depositOverPerTransactionLimit_failsWithTxnLimitExceeded() {
        BankException ex = assertThrows(BankException.class,
                () -> depositService.deposit("799273987138", new BigDecimal("70000.01")));

        assertEquals(ErrorCode.TXN_LIMIT_EXCEEDED, ex.getErrorCode());
        assertEquals(422, ex.getErrorCode().getHttpStatus().value());
        verify(accountRepository, never()).save(any(Account.class));
        verify(ledgerRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    void depositIntoFixedDeposit_failsWithFdDepositNotAllowed() {
        account.setType(AccountType.FIXED_DEPOSIT);

        BankException ex = assertThrows(BankException.class,
                () -> depositService.deposit("799273987138", new BigDecimal("100.00")));

        assertEquals(ErrorCode.FD_DEPOSIT_NOT_ALLOWED, ex.getErrorCode());
        assertEquals(422, ex.getErrorCode().getHttpStatus().value());
        verify(ledgerRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    void depositIntoFrozenAccount_failsWithAccountFrozen() {
        account.setStatus(AccountStatus.FROZEN);

        BankException ex = assertThrows(BankException.class,
                () -> depositService.deposit("799273987138", new BigDecimal("100.00")));

        assertEquals(ErrorCode.ACCOUNT_FROZEN, ex.getErrorCode());
        assertEquals(422, ex.getErrorCode().getHttpStatus().value());
        verify(ledgerRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    void depositIntoClosedAccount_failsWithAccountClosed() {
        account.setStatus(AccountStatus.CLOSED);

        BankException ex = assertThrows(BankException.class,
                () -> depositService.deposit("799273987138", new BigDecimal("100.00")));

        assertEquals(ErrorCode.ACCOUNT_CLOSED, ex.getErrorCode());
        assertEquals(422, ex.getErrorCode().getHttpStatus().value());
    }

    @Test
    void depositWithZeroNegativeOrTooManyDecimals_failsWithInvalidAmount() {
        assertEquals(ErrorCode.INVALID_AMOUNT, assertThrows(BankException.class,
                () -> depositService.deposit("799273987138", BigDecimal.ZERO)).getErrorCode());
        assertEquals(ErrorCode.INVALID_AMOUNT, assertThrows(BankException.class,
                () -> depositService.deposit("799273987138", new BigDecimal("-1.00"))).getErrorCode());
        assertEquals(ErrorCode.INVALID_AMOUNT, assertThrows(BankException.class,
                () -> depositService.deposit("799273987138", new BigDecimal("10.005"))).getErrorCode());
    }

    @Test
    void depositIntoUnknownAccount_failsWithAccountNotFound() {
        when(accountRepository.findByAccountNumber("000000000000")).thenReturn(Optional.empty());

        BankException ex = assertThrows(BankException.class,
                () -> depositService.deposit("000000000000", new BigDecimal("100.00")));

        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND, ex.getErrorCode());
    }
}
