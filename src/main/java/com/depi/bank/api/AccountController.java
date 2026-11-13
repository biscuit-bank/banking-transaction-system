package com.depi.bank.api;

import com.depi.bank.domain.account.Account;
import com.depi.bank.domain.account.AccountType;
import com.depi.bank.exception.BankException;
import com.depi.bank.exception.ErrorCode;
import com.depi.bank.service.AccountOpeningService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * US-06 / US-07: open Savings and Checking accounts.
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountOpeningService accountOpeningService;

    public AccountController(AccountOpeningService accountOpeningService) {
        this.accountOpeningService = accountOpeningService;
    }

    public record OpenAccountRequest(
            @NotNull AccountType accountType,
            @NotNull BigDecimal openingDepositEgp
    ) {}

    public record AccountResponse(
            UUID id,
            String accountNumber,
            String accountType,
            String status,
            BigDecimal balance,
            String currency,
            BigDecimal annualInterestRate
    ) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse openAccount(@Valid @RequestBody OpenAccountRequest request,
                                       Authentication authentication) {
        UUID customerId = resolveCustomerId(authentication);
        BigDecimal openingDeposit = request.openingDepositEgp();

        Account account;
        if (request.accountType() == AccountType.SAVINGS) {
            account = accountOpeningService.openSavingsAccount(customerId, openingDeposit);
        } else if (request.accountType() == AccountType.CURRENT
                || request.accountType() == AccountType.CHECKING) {
            account = accountOpeningService.openCheckingAccount(customerId, openingDeposit);
        } else {
            throw new BankException(ErrorCode.VALIDATION_ERROR,
                    "Only Savings and Checking accounts can be opened through this endpoint.");
        }

        return toResponse(account);
    }

    private AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getType().name(),
                account.getStatus().name(),
                account.getBalance(),
                account.getCurrency(),
                account.getAnnualInterestRate());
    }

    private UUID resolveCustomerId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BankException(ErrorCode.INVALID_CREDENTIALS);
        }
        return UUID.nameUUIDFromBytes(
                authentication.getName().trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}
