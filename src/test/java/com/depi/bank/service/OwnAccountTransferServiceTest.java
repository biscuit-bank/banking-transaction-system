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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OwnAccountTransferServiceTest {

    private AccountRepository accountRepository;
    private LedgerRepository ledgerRepository;
    private OwnAccountTransferService ownAccountTransferService;
    private Account source;
    private Account destination;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        ledgerRepository = mock(LedgerRepository.class);
        ownAccountTransferService = new OwnAccountTransferService(accountRepository, ledgerRepository);

        UUID customerId = UUID.randomUUID();

        source = new Account();
        source.setAccountNumber("799273987130");
        source.setCustomerId(customerId);
        source.setType(AccountType.CURRENT);
        source.setStatus(AccountStatus.ACTIVE);
        source.setBalance(new BigDecimal("500.00"));
        source.setCurrency("EGP");
        source.setAnnualInterestRate(BigDecimal.ZERO);

        destination = new Account();
        destination.setAccountNumber("799273987138");
        destination.setCustomerId(customerId);
        destination.setType(AccountType.SAVINGS);
        destination.setStatus(AccountStatus.ACTIVE);
        destination.setBalance(new BigDecimal("2000.00"));
        destination.setCurrency("EGP");
        destination.setAnnualInterestRate(new BigDecimal("0.1700"));

        when(accountRepository.findByAccountNumber("799273987130")).thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumber("799273987138")).thenReturn(Optional.of(destination));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void validOwnTransfer_createsTransferOutAndInLedgerEntriesWithZeroFee() {
        OwnAccountTransferService.TransferResult result =
                ownAccountTransferService.transfer("799273987130", "799273987138", new BigDecimal("125.50"));

        assertEquals(0, result.feeEgp().compareTo(new BigDecimal("0.00")));
        assertEquals(0, result.sourceBalanceAfter().compareTo(new BigDecimal("374.50")));
        assertEquals(0, result.destinationBalanceAfter().compareTo(new BigDecimal("2125.50")));

        ArgumentCaptor<LedgerEntry> ledgerCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository, times(2)).save(ledgerCaptor.capture());

        List<LedgerEntry> entries = ledgerCaptor.getAllValues();
        assertEquals(TransactionType.TRANSFER_OUT, entries.get(0).getType());
        assertEquals(TransactionType.TRANSFER_IN, entries.get(1).getType());
    }

    @Test
    void sourceEqualsDestination_failsWithSameAccountTransfer() {
        BankException ex = assertThrows(BankException.class,
                () -> ownAccountTransferService.transfer("799273987130", "799273987130", new BigDecimal("10.00")));

        assertEquals(ErrorCode.SAME_ACCOUNT_TRANSFER, ex.getErrorCode());
        verify(accountRepository, never()).save(any(Account.class));
        verify(ledgerRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    void validationFailure_doesNotMutateAnyAccountBalance() {
        BigDecimal sourceBefore = source.getBalance();
        BigDecimal destinationBefore = destination.getBalance();

        BankException ex = assertThrows(BankException.class,
                () -> ownAccountTransferService.transfer("799273987130", "799273987138", new BigDecimal("2000.00")));

        assertEquals(ErrorCode.OVERDRAFT_LIMIT_EXCEEDED, ex.getErrorCode());
        assertEquals(0, source.getBalance().compareTo(sourceBefore));
        assertEquals(0, destination.getBalance().compareTo(destinationBefore));
        verify(accountRepository, never()).save(any(Account.class));
        verify(ledgerRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    void transferringFromSavingsBelowMinimumBalance_failsWithMinBalanceViolation() {
        source.setType(AccountType.SAVINGS);
        source.setBalance(new BigDecimal("1500.00"));

        BankException ex = assertThrows(BankException.class,
                () -> ownAccountTransferService.transfer(
                        "799273987130", "799273987138", new BigDecimal("500.01")));

        assertEquals(ErrorCode.MIN_BALANCE_VIOLATION, ex.getErrorCode());
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void transferFromFrozenSource_failsWithAccountFrozen() {
        source.setStatus(AccountStatus.FROZEN);

        BankException ex = assertThrows(BankException.class,
                () -> ownAccountTransferService.transfer(
                        "799273987130", "799273987138", new BigDecimal("10.00")));

        assertEquals(ErrorCode.ACCOUNT_FROZEN, ex.getErrorCode());
        verify(ledgerRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    void transferBetweenDifferentCustomers_failsAsNotFound() {
        destination.setCustomerId(UUID.randomUUID());

        BankException ex = assertThrows(BankException.class,
                () -> ownAccountTransferService.transfer(
                        "799273987130", "799273987138", new BigDecimal("10.00")));

        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND, ex.getErrorCode());
        verify(ledgerRepository, never()).save(any(LedgerEntry.class));
    }
}
