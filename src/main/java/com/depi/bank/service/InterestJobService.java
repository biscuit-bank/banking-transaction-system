package com.depi.bank.service;

import com.depi.bank.domain.Account;
import com.depi.bank.domain.AuditLog;
import com.depi.bank.domain.Transaction;
import com.depi.bank.repository.AccountRepository;
import com.depi.bank.repository.AuditLogRepository;
import com.depi.bank.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InterestJobService {

    private static final BigDecimal SAVINGS_ANNUAL_RATE = new BigDecimal("0.17");
    private static final BigDecimal SAVINGS_MIN_BALANCE = new BigDecimal("1000.00");
    private static final BigDecimal FD_12M_RATE = new BigDecimal("0.17");
    private static final BigDecimal FD_36M_RATE = new BigDecimal("0.1775");
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public InterestJobService(AccountRepository accountRepository,
                              TransactionRepository transactionRepository,
                              AuditLogRepository auditLogRepository,
                              Clock clock) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> runMonthlyInterestAndFdMaturity(String adminIdentifier, String clientIp) {
        YearMonth currentMonth = YearMonth.now(clock);
        String monthKey = currentMonth.toString();
        LocalDate today = LocalDate.now(clock);
        Instant nowInstant = Instant.now(clock);

        List<Account> activeAccounts = accountRepository.findByStatus("ACTIVE");

        int savingsProcessed = 0;
        int fdProcessed = 0;
        int duplicatesSkipped = 0;

        for (Account account : activeAccounts) {
            if ("SAVINGS".equalsIgnoreCase(account.getAccountType())) {
                if (monthKey.equals(account.getLastInterestPostedMonth())) {
                    duplicatesSkipped++;
                    continue;
                }
                if (account.getBalance().compareTo(SAVINGS_MIN_BALANCE) >= 0) {
                    BigDecimal monthlyInterest = account.getBalance()
                            .multiply(SAVINGS_ANNUAL_RATE)
                            .divide(TWELVE, 2, RoundingMode.HALF_UP);

                    BigDecimal newBalance = account.getBalance().add(monthlyInterest);
                    account.setBalance(newBalance);
                    account.setLastInterestPostedMonth(monthKey);
                    accountRepository.save(account);

                    String ref = generateReference(today);
                    transactionRepository.save(new Transaction(
                            account.getAccountNumber(), ref, "INTEREST", monthlyInterest,
                            newBalance, "Monthly savings interest (" + monthKey + ")", nowInstant
                    ));
                    savingsProcessed++;
                }

            } else if ("FIXED_DEPOSIT".equalsIgnoreCase(account.getAccountType())) {
                if (!account.isInterestCredited()
                        && account.getMaturityDate() != null
                        && !account.getMaturityDate().isAfter(today)) {

                    int months = (account.getTermMonths() != null) ? account.getTermMonths() : 12;
                    BigDecimal rate = (months == 36) ? FD_36M_RATE : FD_12M_RATE;

                    BigDecimal termFraction = BigDecimal.valueOf(months).divide(TWELVE, 6, RoundingMode.HALF_UP);
                    BigDecimal fdInterest = account.getBalance()
                            .multiply(rate)
                            .multiply(termFraction)
                            .setScale(2, RoundingMode.HALF_UP);

                    BigDecimal newBalance = account.getBalance().add(fdInterest);
                    account.setBalance(newBalance);
                    account.setInterestCredited(true);
                    accountRepository.save(account);

                    String ref = generateReference(today);
                    transactionRepository.save(new Transaction(
                            account.getAccountNumber(), ref, "INTEREST", fdInterest,
                            newBalance, "Fixed Deposit maturity interest (" + months + "m)", nowInstant
                    ));
                    fdProcessed++;
                }
            }
        }

        String reason = String.format("Manual interest job: savings=%d, fd=%d, skipped=%d",
                savingsProcessed, fdProcessed, duplicatesSkipped);
        auditLogRepository.save(new AuditLog(
                null, adminIdentifier, "MANUAL_INTEREST_TRIGGER", "SYSTEM_BATCH",
                "SUCCESS", null, reason, clientIp, nowInstant
        ));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", "COMPLETED");
        summary.put("month", monthKey);
        summary.put("savingsAccountsCredited", savingsProcessed);
        summary.put("fixedDepositsCredited", fdProcessed);
        summary.put("duplicatesSkipped", duplicatesSkipped);
        summary.put("executedBy", adminIdentifier);
        summary.put("executedAt", nowInstant.toString());
        return summary;
    }

    private String generateReference(LocalDate date) {
        String datePart = date.format(DateTimeFormatter.BASIC_ISO_DATE);
        String randomPart = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return "TXN-" + datePart + "-" + randomPart;
    }
}
