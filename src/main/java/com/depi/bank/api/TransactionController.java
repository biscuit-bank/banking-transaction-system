package com.depi.bank.api;

import com.depi.bank.domain.account.Account;
import com.depi.bank.service.CheckingWithdrawalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * US-13: checking-account withdrawal with overdraft and velocity limits.
 */
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final CheckingWithdrawalService checkingWithdrawalService;

    public TransactionController(CheckingWithdrawalService checkingWithdrawalService) {
        this.checkingWithdrawalService = checkingWithdrawalService;
    }

    public record WithdrawRequest(
            @NotBlank String accountNumber,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amountEgp
    ) {}

    public record WithdrawResponse(
            String accountNumber,
            BigDecimal amountEgp,
            BigDecimal balanceAfter,
            String currency
    ) {}

    @PostMapping("/withdraw")
    @ResponseStatus(HttpStatus.CREATED)
    public WithdrawResponse withdraw(@Valid @RequestBody WithdrawRequest request) {
        Account account = checkingWithdrawalService.withdraw(request.accountNumber(), request.amountEgp());
        return new WithdrawResponse(
                account.getAccountNumber(),
                request.amountEgp().setScale(2, RoundingMode.HALF_EVEN),
                account.getBalance(),
                account.getCurrency()
        );
    }
}
