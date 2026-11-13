package com.depi.bank.service;

import com.depi.bank.domain.account.Account;
import com.depi.bank.domain.account.AccountStatus;
import com.depi.bank.domain.account.AccountType;
import com.depi.bank.domain.account.LedgerEntry;
import com.depi.bank.exception.BankException;
import com.depi.bank.exception.ErrorCode;
import com.depi.bank.repository.AccountRepository;
import com.depi.bank.repository.LedgerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Service
public class OwnAccountTransferService {

    private static final BigDecimal MIN_SAVINGS_BALANCE = new BigDecimal("1000.00");
    private static final BigDecimal CHECKING_OVERDRAFT_LIMIT = new BigDecimal("-1000.00");
    private static final String DEFAULT_CURRENCY = "EGP";

    private final AccountRepository accountRepository;
    private final LedgerRepository ledgerRepository;

    public OwnAccountTransferService(AccountRepository accountRepository, LedgerRepository ledgerRepository) {
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional
    public TransferResult transfer(String sourceAccountNumber, String destinationAccountNumber, BigDecimal amountEgp) {
        if (sourceAccountNumber == null || destinationAccountNumber == null
                || sourceAccountNumber.isBlank() || destinationAccountNumber.isBlank()) {
            throw new BankException(ErrorCode.INVALID_ACCOUNT_NUMBER);
        }
        if (sourceAccountNumber.equals(destinationAccountNumber)) {
            throw new BankException(ErrorCode.SAME_ACCOUNT_TRANSFER);
        }
        if (amountEgp == null || amountEgp.scale() > 2 || amountEgp.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BankException(ErrorCode.INVALID_AMOUNT);
        }

        BigDecimal amount = amountEgp.setScale(2, RoundingMode.HALF_EVEN);
        Account source = accountRepository.findByAccountNumber(sourceAccountNumber)
                .orElseThrow(() -> new BankException(ErrorCode.ACCOUNT_NOT_FOUND));
        Account destination = accountRepository.findByAccountNumber(destinationAccountNumber)
                .orElseThrow(() -> new BankException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (!source.getCustomerId().equals(destination.getCustomerId())) {
            throw new BankException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        validateActive(source);
        validateActive(destination);

        validateFixedDepositSource(source, amount);

        BigDecimal sourceAfter = source.getBalance().subtract(amount).setScale(2, RoundingMode.HALF_EVEN);
        validateSourceBalance(source, sourceAfter);

        BigDecimal destinationAfter = destination.getBalance().add(amount).setScale(2, RoundingMode.HALF_EVEN);

        source.setBalance(sourceAfter);
        destination.setBalance(destinationAfter);

        accountRepository.save(source);
        accountRepository.save(destination);
        ledgerRepository.save(LedgerEntry.transferOut(source.getId(), amount, sourceAfter, DEFAULT_CURRENCY));
        ledgerRepository.save(LedgerEntry.transferIn(destination.getId(), amount, destinationAfter, DEFAULT_CURRENCY));

        return new TransferResult(sourceAfter, destinationAfter, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN));
    }

    private void validateActive(Account account) {
        if (account.getStatus() == AccountStatus.FROZEN) {
            throw new BankException(ErrorCode.ACCOUNT_FROZEN);
        }
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new BankException(ErrorCode.ACCOUNT_CLOSED);
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BankException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }
    }

    private void validateFixedDepositSource(Account source, BigDecimal amount) {
        if (source.getType() != AccountType.FIXED_DEPOSIT) {
            return;
        }
        Instant now = Instant.now();
        if (source.getMaturityDate() == null || now.isBefore(source.getMaturityDate())
                || source.getMaturityInterestCreditedAt() == null) {
            throw new BankException(ErrorCode.FD_NOT_MATURE);
        }
        if (amount.compareTo(source.getBalance()) != 0) {
            throw new BankException(ErrorCode.FD_PARTIAL_WITHDRAWAL_NOT_ALLOWED);
        }
    }

    private void validateSourceBalance(Account source, BigDecimal sourceAfter) {
        if (source.getType() == AccountType.SAVINGS && sourceAfter.compareTo(MIN_SAVINGS_BALANCE) < 0) {
            throw new BankException(ErrorCode.MIN_BALANCE_VIOLATION);
        }
        if ((source.getType() == AccountType.CURRENT || source.getType() == AccountType.CHECKING)
                && sourceAfter.compareTo(CHECKING_OVERDRAFT_LIMIT) < 0) {
            throw new BankException(ErrorCode.OVERDRAFT_LIMIT_EXCEEDED);
        }
    }

    public record TransferResult(BigDecimal sourceBalanceAfter, BigDecimal destinationBalanceAfter, BigDecimal feeEgp) {
    }
}
