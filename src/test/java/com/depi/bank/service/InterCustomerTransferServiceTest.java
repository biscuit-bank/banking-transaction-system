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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterCustomerTransferServiceTest {

    private AccountRepository accountRepository;
    private LedgerRepository ledgerRepository;
    private InterCustomerTransferService service;
    private Account source;
    private Account destination;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        ledgerRepository = mock(LedgerRepository.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-13T02:00:00Z"), ZoneId.of("Africa/Cairo"));
        service = new InterCustomerTransferService(accountRepository, ledgerRepository, fixedClock,
                new BigDecimal("70000.00"), new BigDecimal("120000.00"), new BigDecimal("400000.00"),
                new BigDecimal("-1000.00"));

        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        source = new Account();
        source.setAccountNumber("799273987130");
        source.setCustomerId(sender);
        source.setType(AccountType.CURRENT);
        source.setStatus(AccountStatus.ACTIVE);
        source.setBalance(new BigDecimal("100000.00"));
        destination = new Account();
        destination.setAccountNumber("799273987138");
        destination.setCustomerId(receiver);
        destination.setType(AccountType.SAVINGS);
        destination.setStatus(AccountStatus.ACTIVE);
        destination.setBalance(new BigDecimal("2000.00"));

        when(accountRepository.findByAccountNumber("799273987130")).thenReturn(Optional.of(source));
        when(accountRepository.findByAccountNumber("799273987138")).thenReturn(Optional.of(destination));
        when(accountRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(ledgerRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(ledgerRepository.findByAccountIdAndTypeInAndCreatedAtBetween(any(), any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void feeMin() {
        assertEquals(0, service.calculateFee(new BigDecimal("100.00")).compareTo(new BigDecimal("0.50")));
    }

    @Test
    void feeCap() {
        assertEquals(0, service.calculateFee(new BigDecimal("50000.00")).compareTo(new BigDecimal("20.00")));
    }

    @Test
    void feeRounding() {
        assertEquals(0, service.calculateFee(new BigDecimal("1235.00")).compareTo(new BigDecimal("1.24")));
    }

    @Test
    void threeLedgerEntries() {
        InterCustomerTransferService.TransferResult r = service.transfer("799273987130", "799273987138", new BigDecimal("1000.00"));
        assertEquals(0, r.feeEgp().compareTo(new BigDecimal("1.00")));
        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository, times(3)).save(captor.capture());
        assertEquals(TransactionType.TRANSFER_OUT, captor.getAllValues().get(0).getType());
        assertEquals(TransactionType.TRANSFER_IN, captor.getAllValues().get(1).getType());
        assertEquals(TransactionType.FEE, captor.getAllValues().get(2).getType());
    }

    @Test
    void overPerTransactionLimit_fails() {
        BankException ex = assertThrows(BankException.class,
                () -> service.transfer("799273987130", "799273987138", new BigDecimal("70000.01")));
        assertEquals(ErrorCode.TXN_LIMIT_EXCEEDED, ex.getErrorCode());
    }

    @Test
    void dailyLimit_fails() {
        LedgerEntry existing = LedgerEntry.transferOut(UUID.randomUUID(), new BigDecimal("119900.00"),
                new BigDecimal("0.00"), "EGP");
        when(ledgerRepository.findByAccountIdAndTypeInAndCreatedAtBetween(any(), any(), any(), any()))
                .thenReturn(List.of(existing), List.of(existing));
        BankException ex = assertThrows(BankException.class,
                () -> service.transfer("799273987130", "799273987138", new BigDecimal("200.00")));
        assertEquals(ErrorCode.DAILY_LIMIT_EXCEEDED, ex.getErrorCode());
    }

    @Test
    void monthlyLimit_fails() {
        LedgerEntry monthly = LedgerEntry.transferOut(UUID.randomUUID(), new BigDecimal("399900.00"),
                new BigDecimal("0.00"), "EGP");
        when(ledgerRepository.findByAccountIdAndTypeInAndCreatedAtBetween(any(), any(), any(), any()))
                .thenReturn(List.of(), List.of(monthly));
        BankException ex = assertThrows(BankException.class,
                () -> service.transfer("799273987130", "799273987138", new BigDecimal("200.00")));
        assertEquals(ErrorCode.MONTHLY_LIMIT_EXCEEDED, ex.getErrorCode());
    }
}
