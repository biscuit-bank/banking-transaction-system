package com.depi.bank.service;

import com.depi.bank.domain.account.Account;
import com.depi.bank.domain.account.AccountStatus;
import com.depi.bank.domain.account.AccountType;
import com.depi.bank.domain.account.LedgerEntry;
import com.depi.bank.domain.account.TransactionType;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterestPostingServiceTest {

    private static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");
    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

    private AccountRepository accountRepository;
    private LedgerRepository ledgerRepository;
    private InterestPostingService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        ledgerRepository = mock(LedgerRepository.class);
        service = new InterestPostingService(accountRepository, ledgerRepository, Clock.fixed(NOW, CAIRO));

        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerRepository.existsByAccountIdAndTypeAndCreatedAtBetween(any(), eq(TransactionType.INTEREST), any(), any()))
                .thenReturn(false);
        when(ledgerRepository.existsByAccountIdAndType(any(), eq(TransactionType.INTEREST))).thenReturn(false);
    }

    @Test
    void monthlySavingsInterest_creditsActiveSavingsWithHalfUpRounding() {
        Account savings = savingsAccount(new BigDecimal("10000.00"));
        when(accountRepository.findByTypeAndStatus(AccountType.SAVINGS, AccountStatus.ACTIVE))
                .thenReturn(List.of(savings));

        int credited = service.postMonthlySavingsInterest();

        assertEquals(1, credited);
        assertEquals(0, savings.getBalance().compareTo(new BigDecimal("10141.67")));
        assertEquals("2026-11", savings.getLastInterestMonth());

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository).save(captor.capture());
        LedgerEntry entry = captor.getValue();
        assertEquals(TransactionType.INTEREST, entry.getType());
        assertEquals(0, entry.getAmount().compareTo(new BigDecimal("141.67")));
        assertEquals(0, entry.getBalanceAfter().compareTo(new BigDecimal("10141.67")));
        assertEquals("EGP", entry.getCurrency());
        assertEquals(NOW, entry.getCreatedAt());
    }

    @Test
    void monthlySavingsInterest_usesBalanceTimesRateDividedByTwelve() {
        Account savings = savingsAccount(new BigDecimal("1000.00"));
        when(accountRepository.findByTypeAndStatus(AccountType.SAVINGS, AccountStatus.ACTIVE))
                .thenReturn(List.of(savings));

        service.postMonthlySavingsInterest();

        assertEquals(0, savings.getBalance().compareTo(new BigDecimal("1014.17")));
        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository).save(captor.capture());
        assertEquals(0, captor.getValue().getAmount().compareTo(new BigDecimal("14.17")));
    }

    @Test
    void monthlySavingsInterest_skipsAccountBelowMinimumBalance() {
        Account savings = savingsAccount(new BigDecimal("999.99"));
        when(accountRepository.findByTypeAndStatus(AccountType.SAVINGS, AccountStatus.ACTIVE))
                .thenReturn(List.of(savings));

        int credited = service.postMonthlySavingsInterest();

        assertEquals(0, credited);
        assertEquals(0, savings.getBalance().compareTo(new BigDecimal("999.99")));
        verify(ledgerRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    void monthlySavingsInterest_isIdempotentWithinTheSameCalendarMonth() {
        Account savings = savingsAccount(new BigDecimal("10000.00"));
        when(accountRepository.findByTypeAndStatus(AccountType.SAVINGS, AccountStatus.ACTIVE))
                .thenReturn(List.of(savings));

        int firstRun = service.postMonthlySavingsInterest();
        BigDecimal balanceAfterFirstRun = savings.getBalance();
        int secondRun = service.postMonthlySavingsInterest();

        assertEquals(1, firstRun);
        assertEquals(0, secondRun);
        assertEquals(0, savings.getBalance().compareTo(balanceAfterFirstRun));
        verify(ledgerRepository, times(1)).save(any(LedgerEntry.class));
    }

    @Test
    void monthlySavingsInterest_doesNotDuplicateWhenLedgerAlreadyHasInterestForMonth() {
        Account savings = savingsAccount(new BigDecimal("10000.00"));
        when(accountRepository.findByTypeAndStatus(AccountType.SAVINGS, AccountStatus.ACTIVE))
                .thenReturn(List.of(savings));
        when(ledgerRepository.existsByAccountIdAndTypeAndCreatedAtBetween(
                any(), eq(TransactionType.INTEREST), any(), any())).thenReturn(true);

        int credited = service.postMonthlySavingsInterest();

        assertEquals(0, credited);
        assertEquals(0, savings.getBalance().compareTo(new BigDecimal("10000.00")));
        verify(ledgerRepository, never()).save(any(LedgerEntry.class));
        assertEquals("2026-11", savings.getLastInterestMonth());
    }

    @Test
    void monthlySavingsInterest_creditsAgainInTheNextCalendarMonth() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-15T10:00:00Z"), CAIRO);
        InterestPostingService monthAwareService =
                new InterestPostingService(accountRepository, ledgerRepository, clock);
        Account savings = savingsAccount(new BigDecimal("10000.00"));
        when(accountRepository.findByTypeAndStatus(AccountType.SAVINGS, AccountStatus.ACTIVE))
                .thenReturn(List.of(savings));

        int octoberRun = monthAwareService.postMonthlySavingsInterest();
        clock.setInstant(Instant.parse("2026-11-15T10:00:00Z"));
        int novemberRun = monthAwareService.postMonthlySavingsInterest();

        assertEquals(1, octoberRun);
        assertEquals(1, novemberRun);
        verify(ledgerRepository, times(2)).save(any(LedgerEntry.class));
        assertEquals("2026-11", savings.getLastInterestMonth());
    }

    @Test
    void fixedDepositMaturity_doesNotCreditBeforeMaturityDate() {
        Account fixedDeposit = fixedDepositAccount(
                new BigDecimal("5000.00"), new BigDecimal("0.1700"), 12,
                Instant.parse("2026-11-14T00:00:00Z"));
        when(accountRepository.findByTypeAndStatus(AccountType.FIXED_DEPOSIT, AccountStatus.ACTIVE))
                .thenReturn(List.of(fixedDeposit));

        int credited = service.postFixedDepositMaturityInterest();

        assertEquals(0, credited);
        assertEquals(0, fixedDeposit.getBalance().compareTo(new BigDecimal("5000.00")));
        assertNull(fixedDeposit.getMaturityInterestCreditedAt());
        verify(ledgerRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    void fixedDepositMaturity_creditsOnMaturityDateIgnoringTimeOfDay() {
        // Maturity is stored at 00:00 UTC (02:00 Cairo); the job runs later the same Cairo date.
        Account fixedDeposit = fixedDepositAccount(
                new BigDecimal("5000.00"), new BigDecimal("0.1700"), 12,
                Instant.parse("2026-11-13T00:00:00Z"));
        when(accountRepository.findByTypeAndStatus(AccountType.FIXED_DEPOSIT, AccountStatus.ACTIVE))
                .thenReturn(List.of(fixedDeposit));

        int credited = service.postFixedDepositMaturityInterest();

        assertEquals(1, credited);
        assertEquals(0, fixedDeposit.getBalance().compareTo(new BigDecimal("5850.00")));
        assertNotNull(fixedDeposit.getMaturityInterestCreditedAt());

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository).save(captor.capture());
        assertEquals(TransactionType.INTEREST, captor.getValue().getType());
        assertEquals(0, captor.getValue().getAmount().compareTo(new BigDecimal("850.00")));
        assertEquals(0, captor.getValue().getBalanceAfter().compareTo(new BigDecimal("5850.00")));
        assertEquals(NOW, captor.getValue().getCreatedAt());
    }

    @Test
    void fixedDepositMaturity_creditsThirtySixMonthTermInterest() {
        Account fixedDeposit = fixedDepositAccount(
                new BigDecimal("5000.00"), new BigDecimal("0.1775"), 36,
                Instant.parse("2026-11-01T00:00:00Z"));
        when(accountRepository.findByTypeAndStatus(AccountType.FIXED_DEPOSIT, AccountStatus.ACTIVE))
                .thenReturn(List.of(fixedDeposit));

        service.postFixedDepositMaturityInterest();

        assertEquals(0, fixedDeposit.getBalance().compareTo(new BigDecimal("7662.50")));
        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository).save(captor.capture());
        assertEquals(0, captor.getValue().getAmount().compareTo(new BigDecimal("2662.50")));
    }

    @Test
    void fixedDepositMaturity_isCreditedExactlyOnce() {
        Account fixedDeposit = fixedDepositAccount(
                new BigDecimal("5000.00"), new BigDecimal("0.1700"), 12,
                Instant.parse("2026-11-13T00:00:00Z"));
        when(accountRepository.findByTypeAndStatus(AccountType.FIXED_DEPOSIT, AccountStatus.ACTIVE))
                .thenReturn(List.of(fixedDeposit));

        int firstRun = service.postFixedDepositMaturityInterest();
        BigDecimal balanceAfterFirstRun = fixedDeposit.getBalance();
        int secondRun = service.postFixedDepositMaturityInterest();

        assertEquals(1, firstRun);
        assertEquals(0, secondRun);
        assertEquals(0, fixedDeposit.getBalance().compareTo(balanceAfterFirstRun));
        verify(ledgerRepository, times(1)).save(any(LedgerEntry.class));
    }

    @Test
    void fixedDepositMaturity_marksMaturedWithoutDuplicateWhenLedgerAlreadyExists() {
        Account fixedDeposit = fixedDepositAccount(
                new BigDecimal("5000.00"), new BigDecimal("0.1700"), 12,
                Instant.parse("2026-11-13T00:00:00Z"));
        when(accountRepository.findByTypeAndStatus(AccountType.FIXED_DEPOSIT, AccountStatus.ACTIVE))
                .thenReturn(List.of(fixedDeposit));
        when(ledgerRepository.existsByAccountIdAndType(any(), eq(TransactionType.INTEREST))).thenReturn(true);

        int credited = service.postFixedDepositMaturityInterest();

        assertEquals(0, credited);
        assertEquals(0, fixedDeposit.getBalance().compareTo(new BigDecimal("5000.00")));
        assertNotNull(fixedDeposit.getMaturityInterestCreditedAt());
        verify(ledgerRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    void runMonthlyInterestJobs_creditsSavingsAndMaturedFixedDepositsTogether() {
        Account savings = savingsAccount(new BigDecimal("10000.00"));
        Account fixedDeposit = fixedDepositAccount(
                new BigDecimal("5000.00"), new BigDecimal("0.1700"), 12,
                Instant.parse("2026-11-01T00:00:00Z"));
        when(accountRepository.findByTypeAndStatus(AccountType.SAVINGS, AccountStatus.ACTIVE))
                .thenReturn(List.of(savings));
        when(accountRepository.findByTypeAndStatus(AccountType.FIXED_DEPOSIT, AccountStatus.ACTIVE))
                .thenReturn(List.of(fixedDeposit));

        InterestPostingService.InterestJobResult result = service.runMonthlyInterestJobs();

        assertEquals(1, result.savingsAccountsCredited());
        assertEquals(1, result.fixedDepositsCredited());
        assertEquals(2, result.totalCredited());
    }

    private Account savingsAccount(BigDecimal balance) {
        Account account = new Account();
        account.setAccountNumber("799273987130");
        account.setCustomerId(UUID.randomUUID());
        account.setType(AccountType.SAVINGS);
        account.setStatus(AccountStatus.ACTIVE);
        account.setBalance(balance);
        account.setCurrency("EGP");
        account.setAnnualInterestRate(new BigDecimal("0.1700"));
        account.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return account;
    }

    private Account fixedDepositAccount(BigDecimal principal, BigDecimal annualRate,
                                        int termMonths, Instant maturityDate) {
        Account account = new Account();
        account.setAccountNumber("799273987138");
        account.setCustomerId(UUID.randomUUID());
        account.setType(AccountType.FIXED_DEPOSIT);
        account.setStatus(AccountStatus.ACTIVE);
        account.setBalance(principal);
        account.setPrincipalAmount(principal);
        account.setCurrency("EGP");
        account.setAnnualInterestRate(annualRate);
        account.setTermMonths(termMonths);
        account.setMaturityDate(maturityDate);
        account.setCreatedAt(Instant.parse("2025-01-01T00:00:00Z"));
        return account;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        private void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId newZone) {
            return new MutableClock(instant, newZone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
