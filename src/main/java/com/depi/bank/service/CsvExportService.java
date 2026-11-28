package com.depi.bank.service;

import com.depi.bank.domain.Account;
import com.depi.bank.domain.Transaction;
import com.depi.bank.exception.BankException;
import com.depi.bank.exception.ErrorCode;
import com.depi.bank.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class CsvExportService {

    private final TransactionRepository transactionRepository;
    private final StatementService statementService;
    private final Clock clock;

    public CsvExportService(TransactionRepository transactionRepository,
                            StatementService statementService,
                            Clock clock) {
        this.transactionRepository = transactionRepository;
        this.statementService = statementService;
        this.clock = clock;
    }

    public byte[] exportStatementToUtf8Csv(String accountNumber,
                                           String authenticatedEmail,
                                           LocalDate startDate,
                                           LocalDate endDate,
                                           String type) {
        Account account = statementService.verifyOwnership(accountNumber, authenticatedEmail);

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BankException(ErrorCode.VALIDATION_ERROR, "Start date must not be after end date.");
        }

        Instant startInstant = (startDate != null)
                ? startDate.atStartOfDay(clock.getZone()).toInstant()
                : Instant.EPOCH;
        Instant endInstant = (endDate != null)
                ? endDate.plusDays(1).atStartOfDay(clock.getZone()).toInstant().minusNanos(1)
                : Instant.parse("2100-12-31T23:59:59Z");

        List<Transaction> transactions = (type != null && !type.trim().isEmpty())
                ? transactionRepository.findByAccountNumberAndTypeIgnoreCaseAndCreatedAtBetweenOrderByCreatedAtDesc(
                account.getAccountNumber(), type.trim(), startInstant, endInstant)
                : transactionRepository.findByAccountNumberAndCreatedAtBetweenOrderByCreatedAtDesc(
                account.getAccountNumber(), startInstant, endInstant);

        StringBuilder csv = new StringBuilder();
        csv.append('\uFEFF');
        csv.append("Timestamp,Reference,Type,Amount (EGP),Balance After (EGP),Description\n");

        for (Transaction tx : transactions) {
            csv.append(sanitizeCsvField(tx.getCreatedAt() != null ? tx.getCreatedAt().toString() : "")).append(",")
                    .append(sanitizeCsvField(tx.getReference())).append(",")
                    .append(sanitizeCsvField(tx.getType())).append(",")
                    .append(tx.getAmount() != null ? tx.getAmount().toPlainString() : "0.00").append(",")
                    .append(tx.getBalanceAfter() != null ? tx.getBalanceAfter().toPlainString() : "0.00").append(",")
                    .append(sanitizeCsvField(tx.getDescription()))
                    .append("\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String sanitizeCsvField(String value) {
        if (value == null) return "\"\"";
        String sanitized = value.replace("\"", "\"\"");
        if (!sanitized.isEmpty()) {
            char firstChar = sanitized.charAt(0);
            if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@') {
                sanitized = "'" + sanitized;
            }
        }
        return "\"" + sanitized + "\"";
    }
}
