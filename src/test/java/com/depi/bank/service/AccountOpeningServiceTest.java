package com.depi.bank.service;

import com.depi.bank.domain.LuhnAccountNumberService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountOpeningServiceTest {

    private AccountRepository accountRepository;
    private LedgerRepository ledgerRepository;
    private LuhnAccountNumberService luhnService;
    private AccountOpeningService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        ledgerRepository = mock(LedgerRepository.class);
        luhnService = mock(LuhnAccountNumberService.class);

        when(accountRepository.countByCustomerIdAndStatusNot(any(), any())).thenReturn(0L);
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(luhnService.generateDeterministic(anyLong())).thenReturn("799273987130");
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service = new AccountOpeningService(accountRepository, ledgerRepository, luhnService);
    }

    @Test
    void openCheckingAccount_withZeroBalance_createsActiveAccountWithoutLedgerEntry() {
        Account account = service.openCheckingAccount(UUID.randomUUID(), BigDecimal.ZERO.setScale(2));

        assertEquals(AccountType.CURRENT, account.getType());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertEquals(0, account.getBalance().compareTo(BigDecimal.ZERO));
        assertEquals(0, account.getAnnualInterestRate().compareTo(BigDecimal.ZERO));
        assertEquals("799273987130", account.getAccountNumber());
        verify(ledgerRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    void openCheckingAccount_withPositiveBalance_recordsInitialDepositLedgerEntry() {
        Account account = service.openCheckingAccount(UUID.randomUUID(), new BigDecimal("250.00"));

        assertEquals(new BigDecimal("250.00"), account.getBalance());
        assertEquals(AccountType.CURRENT, account.getType());

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository).save(captor.capture());
        assertEquals(new BigDecimal("250.00"), captor.getValue().getAmount());
    }

    @Test
    void openCheckingAccount_atMaximumOpeningDeposit_succeeds() {
        Account account = service.openCheckingAccount(UUID.randomUUID(), new BigDecimal("100000.00"));

        assertEquals(0, account.getBalance().compareTo(new BigDecimal("100000.00")));
    }

    @Test
    void openCheckingAccount_overMaximumOpeningDeposit_fails() {
        BankException ex = assertThrows(BankException.class,
                () -> service.openCheckingAccount(UUID.randomUUID(), new BigDecimal("100000.01")));

        assertEquals(ErrorCode.INVALID_OPENING_AMOUNT, ex.getErrorCode());
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void openCheckingAccount_withNegativeBalance_fails() {
        BankException ex = assertThrows(BankException.class,
                () -> service.openCheckingAccount(UUID.randomUUID(), new BigDecimal("-1.00")));

        assertEquals(ErrorCode.INVALID_OPENING_AMOUNT, ex.getErrorCode());
    }

    @Test
    void openCheckingAccount_withMoreThanTwoDecimalPlaces_fails() {
        BankException ex = assertThrows(BankException.class,
                () -> service.openCheckingAccount(UUID.randomUUID(), new BigDecimal("10.005")));

        assertEquals(ErrorCode.INVALID_AMOUNT, ex.getErrorCode());
    }

    @Test
    void openSavingsAccount_atMinimumDeposit_succeedsWithInterestRateAndLedgerEntry() {
        Account account = service.openSavingsAccount(UUID.randomUUID(), new BigDecimal("1000.00"));

        assertEquals(AccountType.SAVINGS, account.getType());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertEquals(0, account.getBalance().compareTo(new BigDecimal("1000.00")));
        assertEquals(0, account.getAnnualInterestRate().compareTo(new BigDecimal("0.1700")));
        verify(ledgerRepository).save(any(LedgerEntry.class));
    }

    @Test
    void openSavingsAccount_belowMinimumDeposit_fails() {
        BankException ex = assertThrows(BankException.class,
                () -> service.openSavingsAccount(UUID.randomUUID(), new BigDecimal("999.99")));

        assertEquals(ErrorCode.MIN_BALANCE_VIOLATION, ex.getErrorCode());
        assertEquals(422, ex.getErrorCode().getHttpStatus().value());
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void openAccount_whenCustomerAlreadyHasFiveNonClosedAccounts_fails() {
        when(accountRepository.countByCustomerIdAndStatusNot(any(), any())).thenReturn(5L);

        BankException ex = assertThrows(BankException.class,
                () -> service.openSavingsAccount(UUID.randomUUID(), new BigDecimal("1000.00")));

        assertEquals(ErrorCode.MAX_ACCOUNTS_EXCEEDED, ex.getErrorCode());
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void generatedAccountNumberIsAlwaysTwelveDigitsAndLuhnValid() {
        LuhnAccountNumberService realLuhn = new LuhnAccountNumberService();
        AccountOpeningService realNumberService =
                new AccountOpeningService(accountRepository, ledgerRepository, realLuhn);

        for (int i = 0; i < 100; i++) {
            Account account = realNumberService.openCheckingAccount(
                    UUID.randomUUID(), BigDecimal.ZERO.setScale(2));
            assertNotNull(account.getAccountNumber());
            assertEquals(12, account.getAccountNumber().length());
            assertTrue(realLuhn.isValid(account.getAccountNumber()));
        }
    }
}
