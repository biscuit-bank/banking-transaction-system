package com.depi.bank.service;

import com.depi.bank.domain.account.Account;
import com.depi.bank.domain.account.AccountStatus;
import com.depi.bank.domain.account.AccountType;
import com.depi.bank.exception.BankException;
import com.depi.bank.exception.ErrorCode;
import com.depi.bank.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountClosureServiceTest {

    private static final String ACCOUNT_NUMBER = "799273987130";

    private AccountRepository accountRepository;
    private AuditLogService auditLogService;
    private AccountClosureService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        auditLogService = mock(AuditLogService.class);
        service = new AccountClosureService(accountRepository, auditLogService);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void closesActiveAccountWithZeroBalancePermanently() {
        Account account = account(AccountStatus.ACTIVE, "0.00");
        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.of(account));

        Account closed = service.closeAccount(ACCOUNT_NUMBER);

        assertSame(account, closed);
        assertEquals(AccountStatus.CLOSED, closed.getStatus());
        assertEquals(0, closed.getBalance().compareTo(new BigDecimal("0.00")));
        verify(accountRepository).save(account);
    }

    @Test
    void closingAnAlreadyClosedAccountIsRejected() {
        Account account = account(AccountStatus.CLOSED, "0.00");
        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.of(account));

        BankException ex = assertThrowsBankException(() -> service.closeAccount(ACCOUNT_NUMBER));

        assertEquals(ErrorCode.ACCOUNT_CLOSED, ex.getErrorCode());
        assertEquals(422, ex.getErrorCode().getHttpStatus().value());
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void closingActiveAccountWithPositiveBalanceFailsWithNonZeroBalanceOnClose() {
        Account account = account(AccountStatus.ACTIVE, "250.00");
        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.of(account));

        BankException ex = assertThrowsBankException(() -> service.closeAccount(ACCOUNT_NUMBER));

        assertEquals(ErrorCode.NON_ZERO_BALANCE_ON_CLOSE, ex.getErrorCode());
        assertEquals(422, ex.getErrorCode().getHttpStatus().value());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void closingActiveAccountWithNegativeBalanceFailsWithNonZeroBalanceOnClose() {
        Account account = account(AccountStatus.ACTIVE, "-100.00");
        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.of(account));

        BankException ex = assertThrowsBankException(() -> service.closeAccount(ACCOUNT_NUMBER));

        assertEquals(ErrorCode.NON_ZERO_BALANCE_ON_CLOSE, ex.getErrorCode());
        assertEquals(422, ex.getErrorCode().getHttpStatus().value());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void closingFrozenAccountFailsWithAccountFrozenEvenAtZeroBalance() {
        Account account = account(AccountStatus.FROZEN, "0.00");
        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.of(account));

        BankException ex = assertThrowsBankException(() -> service.closeAccount(ACCOUNT_NUMBER));

        assertEquals(ErrorCode.ACCOUNT_FROZEN, ex.getErrorCode());
        assertEquals(422, ex.getErrorCode().getHttpStatus().value());
        assertEquals(AccountStatus.FROZEN, account.getStatus());
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void unknownAccountNumberReturnsNotFound() {
        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.empty());

        BankException ex = assertThrowsBankException(() -> service.closeAccount(ACCOUNT_NUMBER));

        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void crossCustomerClosureReturnsUniformNotFound() {
        Account account = account(AccountStatus.ACTIVE, "0.00");
        UUID owner = UUID.randomUUID();
        account.setCustomerId(owner);
        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.of(account));

        BankException ex = assertThrowsBankException(
                () -> service.closeAccount(ACCOUNT_NUMBER, UUID.randomUUID()));

        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND, ex.getErrorCode());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void ownerCanCloseOwnZeroBalanceAccountAndClosureIsAudited() {
        Account account = account(AccountStatus.ACTIVE, "0.00");
        UUID owner = UUID.randomUUID();
        account.setCustomerId(owner);
        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER)).thenReturn(Optional.of(account));

        Account closed = service.closeAccount(
                ACCOUNT_NUMBER, owner, "owner@example.com", "192.168.1.10");

        assertEquals(AccountStatus.CLOSED, closed.getStatus());
        verify(auditLogService).logSuccess(
                isNull(), eq("owner@example.com"), eq("ACCOUNT_CLOSE"),
                anyString(), anyString(), eq("192.168.1.10"));
    }

    private Account account(AccountStatus status, String balance) {
        Account account = new Account();
        account.setAccountNumber(ACCOUNT_NUMBER);
        account.setCustomerId(UUID.randomUUID());
        account.setType(AccountType.SAVINGS);
        account.setStatus(status);
        account.setBalance(new BigDecimal(balance));
        account.setCurrency("EGP");
        account.setAnnualInterestRate(new BigDecimal("0.1700"));
        account.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return account;
    }

    private BankException assertThrowsBankException(Runnable action) {
        return org.junit.jupiter.api.Assertions.assertThrows(BankException.class, action::run);
    }
}
