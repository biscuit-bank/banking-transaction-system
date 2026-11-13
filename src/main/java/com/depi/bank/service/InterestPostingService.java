package com.depi.bank.service;

import com.depi.bank.domain.account.Account;
import com.depi.bank.domain.account.AccountStatus;
import com.depi.bank.domain.account.AccountType;
import com.depi.bank.domain.account.LedgerEntry;
import com.depi.bank.domain.account.TransactionType;
import com.depi.bank.repository.AccountRepository;
import com.depi.bank.repository.LedgerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

/**
 * Executes the scheduled interest jobs for JOB-01 / JOB-02.
 *
 * <p>Every date and timestamp is derived from the injected {@link Clock} (NFR-05). Both jobs are
 * idempotent: a repeated execution in the same calendar month (Savings) or after maturity (Fixed
 * Deposit) does not create a second INTEREST ledger row or change any balance.</p>
 */
@Service
public class InterestPostingService {

    private static final String DEFAULT_CURRENCY = "EGP";
    private static final BigDecimal MONTHS_PER_YEAR = new BigDecimal("12");
    private static final BigDecimal DEFAULT_SAVINGS_ANNUAL_RATE = new BigDecimal("0.1700");
    private static final BigDecimal MIN_SAVINGS_BALANCE = new BigDecimal("1000.00");

    private final AccountRepository accountRepository;
    private final LedgerRepository ledgerRepository;
    private final Clock clock;

    public InterestPostingService(AccountRepository accountRepository, LedgerRepository ledgerRepository, Clock clock) {
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
        this.clock = clock;
    }

    /**
     * JOB-01 / BR-08: credit {@code balance × annual rate ÷ 12} (HALF_UP) to every ACTIVE Savings
     * account at or above the minimum balance, at most once per calendar month.
     *
     * @return number of accounts credited during this run
     */
    @Transactional
    public int postMonthlySavingsInterest() {
        return creditMonthlySavingsInterest();
    }

    /**
     * Alias used by the scheduler and the administrative manual trigger.
     */
    @Transactional
    public int runMonthlySavingsInterestJob() {
        return creditMonthlySavingsInterest();
    }

    /**
     * JOB-02 / BR-15: credit {@code principal × annual rate × term_months ÷ 12} (HALF_UP) to a
     * matured Fixed Deposit exactly once, on or after its maturity date.
     *
     * @return number of Fixed Deposits credited during this run
     */
    @Transactional
    public int postFixedDepositMaturityInterest() {
        return creditMaturedFixedDeposits();
    }

    /**
     * Alias used by the scheduler and the administrative manual trigger.
     */
    @Transactional
    public int runFixedDepositMaturityJob() {
        return creditMaturedFixedDeposits();
    }

    /**
     * Runs both jobs inside one database transaction, as used by the admin manual trigger.
     */
    @Transactional
    public InterestJobResult runMonthlyInterestJobs() {
        int savingsCredited = creditMonthlySavingsInterest();
        int fixedDepositsCredited = creditMaturedFixedDeposits();
        return new InterestJobResult(savingsCredited, fixedDepositsCredited);
    }

    private int creditMonthlySavingsInterest() {
        LocalDate today = LocalDate.now(clock);
        ZoneId zone = clock.getZone();
        String period = YearMonth.from(today).toString();
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant();
        Instant nextMonthStart = today.withDayOfMonth(1).plusMonths(1).atStartOfDay(zone).toInstant();
        // The ledger query is inclusive; keep the upper bound inside the current month.
        Instant lastInstantInMonth = nextMonthStart.minusNanos(1);
        Instant postedAt = Instant.now(clock);

        List<Account> savingsAccounts =
                accountRepository.findByTypeAndStatus(AccountType.SAVINGS, AccountStatus.ACTIVE);
        int creditedCount = 0;

        for (Account account : savingsAccounts) {
            if (!isEligibleForMonthlyInterest(account, period)) {
                continue;
            }

            boolean alreadyPosted = ledgerRepository.existsByAccountIdAndTypeAndCreatedAtBetween(
                    account.getId(), TransactionType.INTEREST, monthStart, lastInstantInMonth);
            if (alreadyPosted) {
                markSavingsMonthProcessed(account, period);
                continue;
            }

            BigDecimal balance = account.getBalance();
            BigDecimal annualRate = account.getAnnualInterestRate() != null
                    ? account.getAnnualInterestRate()
                    : DEFAULT_SAVINGS_ANNUAL_RATE;
            BigDecimal interest = balance.multiply(annualRate)
                    .divide(MONTHS_PER_YEAR, 2, RoundingMode.HALF_UP);

            if (interest.compareTo(BigDecimal.ZERO) <= 0) {
                markSavingsMonthProcessed(account, period);
                continue;
            }

            BigDecimal newBalance = balance.add(interest).setScale(2, RoundingMode.HALF_EVEN);
            account.setBalance(newBalance);
            account.setLastInterestMonth(period);
            accountRepository.save(account);
            ledgerRepository.save(LedgerEntry.interest(
                    account.getId(), interest, newBalance, resolveCurrency(account), postedAt));
            creditedCount++;
        }

        return creditedCount;
    }

    private boolean isEligibleForMonthlyInterest(Account account, String period) {
        if (account.getBalance() == null
                || account.getBalance().compareTo(MIN_SAVINGS_BALANCE) < 0) {
            return false;
        }
        // Per-account idempotency guard in addition to the ledger check.
        return !period.equals(account.getLastInterestMonth());
    }

    private void markSavingsMonthProcessed(Account account, String period) {
        account.setLastInterestMonth(period);
        accountRepository.save(account);
    }

    private int creditMaturedFixedDeposits() {
        LocalDate today = LocalDate.now(clock);
        ZoneId zone = clock.getZone();
        Instant now = Instant.now(clock);

        List<Account> fixedDeposits =
                accountRepository.findByTypeAndStatus(AccountType.FIXED_DEPOSIT, AccountStatus.ACTIVE);
        int creditedCount = 0;

        for (Account account : fixedDeposits) {
            if (account.getMaturityDate() == null) {
                continue;
            }

            LocalDate maturityDate = LocalDate.ofInstant(account.getMaturityDate(), zone);
            if (today.isBefore(maturityDate)) {
                continue;
            }
            if (account.getMaturityInterestCreditedAt() != null) {
                continue;
            }

            // Defensive idempotency for data written before the maturity marker existed.
            boolean alreadyCredited = ledgerRepository.existsByAccountIdAndType(
                    account.getId(), TransactionType.INTEREST);
            if (alreadyCredited) {
                markFixedDepositMatured(account, now);
                continue;
            }

            BigDecimal principal = account.getPrincipalAmount() != null
                    ? account.getPrincipalAmount()
                    : account.getBalance();
            Integer termMonths = account.getTermMonths();
            BigDecimal annualRate = account.getAnnualInterestRate();
            if (principal == null || termMonths == null || termMonths <= 0 || annualRate == null) {
                continue;
            }

            BigDecimal interest = principal.multiply(annualRate)
                    .multiply(new BigDecimal(termMonths))
                    .divide(MONTHS_PER_YEAR, 2, RoundingMode.HALF_UP);

            if (interest.compareTo(BigDecimal.ZERO) <= 0) {
                markFixedDepositMatured(account, now);
                continue;
            }

            BigDecimal currentBalance = account.getBalance() != null ? account.getBalance() : principal;
            BigDecimal newBalance = currentBalance.add(interest).setScale(2, RoundingMode.HALF_EVEN);
            account.setBalance(newBalance);
            account.setMaturityInterestCreditedAt(now);
            accountRepository.save(account);
            ledgerRepository.save(LedgerEntry.interest(
                    account.getId(), interest, newBalance, resolveCurrency(account), now));
            creditedCount++;
        }

        return creditedCount;
    }

    private void markFixedDepositMatured(Account account, Instant now) {
        account.setMaturityInterestCreditedAt(now);
        accountRepository.save(account);
    }

    private String resolveCurrency(Account account) {
        return account.getCurrency() != null ? account.getCurrency() : DEFAULT_CURRENCY;
    }

    public record InterestJobResult(int savingsAccountsCredited, int fixedDepositsCredited) {
        public int totalCredited() {
            return savingsAccountsCredited + fixedDepositsCredited;
        }
    }
}
