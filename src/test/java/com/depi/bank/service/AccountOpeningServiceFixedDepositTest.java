package com.depi.bank.service;

import com.depi.bank.domain.LuhnAccountNumberService;
import com.depi.bank.domain.account.Account;
import com.depi.bank.domain.account.AccountType;
import com.depi.bank.exception.BankException;
import com.depi.bank.exception.ErrorCode;
import com.depi.bank.exception.ErrorCode;
import com.depi.bank.repository.AccountRepository;
import com.depi.bank.repository.LedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountOpeningServiceFixedDepositTest {

    private AccountRepository accountRepository;
    private LedgerRepository ledgerRepository;
    private LuhnAccountNumberService luhnService;
    private AccountOpeningService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        ledgerRepository = mock(LedgerRepository.class);
        luhnService = mock(LuhnAccountNumberService.class);
        when(accountRepository.countByCustomerIdAndStatusNot(Mockito.any(), Mockito.any())).thenReturn(0L);
        when(accountRepository.existsByAccountNumber(Mockito.anyString())).thenReturn(false);
        when(luhnService.generateDeterministic(Mockito.anyLong())).thenReturn("799273987130");
        when(accountRepository.save(Mockito.any())).thenAnswer(i -> i.getArgument(0));
        when(ledgerRepository.save(Mockito.any())).thenAnswer(i -> i.getArgument(0));
        service = new AccountOpeningService(accountRepository, ledgerRepository, luhnService);
    }

    @Test
    void openFixedDeposit_12Months() {
        Account a = service.openFixedDepositAccount(UUID.randomUUID(), new BigDecimal("5000.00"), 12);
        assertEquals(AccountType.FIXED_DEPOSIT, a.getType());
        assertEquals(0, a.getAnnualInterestRate().compareTo(new BigDecimal("0.1700")));
        assertEquals(12, a.getTermMonths());
        assertNotNull(a.getMaturityDate());
    }

    @Test
    void openFixedDeposit_36Months() {
        Account a = service.openFixedDepositAccount(UUID.randomUUID(), new BigDecimal("5000.00"), 36);
        assertEquals(0, a.getAnnualInterestRate().compareTo(new BigDecimal("0.1775")));
        assertEquals(36, a.getTermMonths());
    }

    @Test
    void minDepositViolation() {
        BankException ex = org.junit.jupiter.api.Assertions.assertThrows(BankException.class,
                () -> service.openFixedDepositAccount(UUID.randomUUID(), new BigDecimal("4999.99"), 12));
        assertEquals(ErrorCode.MIN_OPENING_DEPOSIT_VIOLATION, ex.getErrorCode());
    }

    @Test
    void invalidTerm() {
        BankException ex = org.junit.jupiter.api.Assertions.assertThrows(BankException.class,
                () -> service.openFixedDepositAccount(UUID.randomUUID(), new BigDecimal("5000.00"), 6));
        assertEquals(ErrorCode.INVALID_FD_TERM, ex.getErrorCode());
    }
}
