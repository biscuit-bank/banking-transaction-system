package com.depi.bank.service;

import com.depi.bank.domain.Account;
import com.depi.bank.domain.Role;
import com.depi.bank.domain.Transaction;
import com.depi.bank.domain.User;
import com.depi.bank.exception.BankException;
import com.depi.bank.exception.ErrorCode;
import com.depi.bank.repository.AccountRepository;
import com.depi.bank.repository.TransactionRepository;
import com.depi.bank.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminDashboardService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final Clock clock;

    public AdminDashboardService(AccountRepository accountRepository,
                                 UserRepository userRepository,
                                 TransactionRepository transactionRepository,
                                 Clock clock) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    public Map<String, Object> getDashboardMetrics() {
        List<Account> allAccounts = accountRepository.findAll();

        long totalCustomers = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.CUSTOMER)
                .count();

        BigDecimal totalReserves = allAccounts.stream()
                .filter(acc -> !"CLOSED".equalsIgnoreCase(acc.getStatus()))
                .map(Account::getBalance)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Long> accountsByStatus = allAccounts.stream()
                .collect(Collectors.groupingBy(Account::getStatus, Collectors.counting()));

        Map<String, Long> accountsByType = allAccounts.stream()
                .collect(Collectors.groupingBy(Account::getAccountType, Collectors.counting()));

        LocalDate today = LocalDate.now(clock);
        Instant startOfDay = today.atStartOfDay(clock.getZone()).toInstant();
        Instant endOfDay = today.plusDays(1).atStartOfDay(clock.getZone()).toInstant().minusNanos(1);

        long transactionsToday = transactionRepository.countByCreatedAtBetween(startOfDay, endOfDay);
        BigDecimal feesCreatedToday = transactionRepository.findFeesCreatedBetween(startOfDay, endOfDay).stream()
                .map(Transaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalReserves", totalReserves);
        metrics.put("totalCustomers", totalCustomers);
        metrics.put("totalAccounts", allAccounts.size());
        metrics.put("accountsByStatus", accountsByStatus);
        metrics.put("accountsByType", accountsByType);
        metrics.put("transactionsToday", transactionsToday);
        metrics.put("feesCreatedToday", feesCreatedToday);
        return metrics;
    }

    public List<Map<String, Object>> searchCustomers(String query) {
        if (query == null || query.trim().length() < 2) {
            throw new BankException(ErrorCode.INVALID_SEARCH_QUERY);
        }
        String q = query.trim().toLowerCase();

        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.CUSTOMER)
                .filter(u -> u.getFullName() != null && u.getFullName().toLowerCase().contains(q))
                .map(u -> {
                    Map<String, Object> dto = new LinkedHashMap<>();
                    dto.put("id", u.getId());
                    dto.put("fullName", u.getFullName());
                    dto.put("email", u.getEmail());
                    dto.put("phone", u.getPhone());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public List<Account> searchAccounts(String accountNumber, String type, String status,
                                        BigDecimal minBalance, BigDecimal maxBalance) {
        if (accountNumber != null && !accountNumber.trim().isEmpty()) {
            if (!AccountFreezeService.isValidLuhnAccountNumber(accountNumber.trim())) {
                throw new BankException(ErrorCode.INVALID_ACCOUNT_NUMBER);
            }
        }

        return accountRepository.findAll().stream()
                .filter(acc -> accountNumber == null || accountNumber.trim().isEmpty()
                        || accountNumber.trim().equals(acc.getAccountNumber()))
                .filter(acc -> type == null || type.trim().isEmpty()
                        || type.trim().equalsIgnoreCase(acc.getAccountType()))
                .filter(acc -> status == null || status.trim().isEmpty()
                        || status.trim().equalsIgnoreCase(acc.getStatus()))
                .filter(acc -> minBalance == null || acc.getBalance().compareTo(minBalance) >= 0)
                .filter(acc -> maxBalance == null || acc.getBalance().compareTo(maxBalance) <= 0)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getRichestCustomers() {
        List<Account> nonClosedAccounts = accountRepository.findByStatusNot("CLOSED");
        if (nonClosedAccounts.isEmpty()) {
            return List.of();
        }

        Map<User, BigDecimal> balancePerCustomer = nonClosedAccounts.stream()
                .collect(Collectors.groupingBy(
                        Account::getUser,
                        Collectors.reducing(BigDecimal.ZERO, Account::getBalance, BigDecimal::add)
                ));

        BigDecimal maxBalance = balancePerCustomer.values().stream()
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        return balancePerCustomer.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(maxBalance) == 0)
                .map(entry -> {
                    User u = entry.getKey();
                    Map<String, Object> dto = new LinkedHashMap<>();
                    dto.put("customerId", u.getId());
                    dto.put("fullName", u.getFullName());
                    dto.put("email", u.getEmail());
                    dto.put("totalBalance", entry.getValue());
                    return dto;
                })
                .collect(Collectors.toList());
    }
}
