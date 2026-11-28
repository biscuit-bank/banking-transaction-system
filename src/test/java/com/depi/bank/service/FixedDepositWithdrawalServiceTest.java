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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FixedDepositWithdrawalServiceTest {

    private AccountRepository accountRepository;
    private LedgerRepository ledgerRepository;
    private FixedDepositWithdrawalService service;
    private Account account;
    private Clock clock;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        ledgerRepository = mock(LedgerRepository.class);
        clock = Clock.fixed(Instant.parse("2026-11-13T02:00:00Z"), ZoneId.of("Africa/Cairo"));
        service = new FixedDepositWithdrawalService(accountRepository, ledgerRepository, clock);

        account = new Account();
        account.setAccountNumber("799273987130");
        account.setCustomerId(UUID.randomUUID());
        account.setType(AccountType.FIXED_DEPOSIT);
        account.setStatus(AccountStatus.ACTIVE);
        account.setBalance(new BigDecimal("5850.00"));
        account.setCurrency("EGP");
        account.setMaturityDate(Instant.parse("2026-10-01T00:00:00Z"));
        account.setMaturityInterestCreditedAt(Instant.parse("2026-10-01T00:00:00Z"));

        when(accountRepository.findByAccountNumber("799273987130")).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(ledgerRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void fullWithdrawal_succeeds() {
        Account updated = service.withdraw("799273987130", new BigDecimal("5850.00"));
        assertEquals(0, updated.getBalance().compareTo(BigDecimal.ZERO));
    }

    @Test
    void partialWithdrawal_fails() {
        BankException ex = assertThrows(BankException.class,
                () -> service.withdraw("799273987130", new BigDecimal("100.00")));
        assertEquals(ErrorCode.FD_PARTIAL_WITHDRAWAL_NOT_ALLOWED, ex.getErrorCode());
    }

    @Test
    void beforeMaturity_fails() {
        account.setMaturityDate(Instant.parse("2027-01-01T00:00:00Z"));
        account.setMaturityInterestCreditedAt(null);
        BankException ex = assertThrows(BankException.class,
                () -> service.withdraw("799273987130", new BigDecimal("5850.00")));
        assertEquals(ErrorCode.FD_NOT_MATURE, ex.getErrorCode());
    }
}
