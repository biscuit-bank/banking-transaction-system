package com.depi.bank.api;

import com.depi.bank.domain.account.Account;
import com.depi.bank.service.CheckingWithdrawalService;
import com.depi.bank.service.DepositService;
import com.depi.bank.service.OwnAccountTransferService;
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

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final DepositService depositService;
    private final CheckingWithdrawalService checkingWithdrawalService;
    private final OwnAccountTransferService ownAccountTransferService;

    public TransactionController(DepositService depositService,
                                 CheckingWithdrawalService checkingWithdrawalService,
                                 OwnAccountTransferService ownAccountTransferService) {
        this.depositService = depositService;
        this.checkingWithdrawalService = checkingWithdrawalService;
        this.ownAccountTransferService = ownAccountTransferService;
    }

    public record DepositRequest(
            @NotBlank String accountNumber,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amountEgp
    ) {}

    public record DepositResponse(
            String accountNumber,
            BigDecimal amountEgp,
            BigDecimal balanceAfter,
            String currency
    ) {}

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

    public record OwnTransferRequest(
            @NotBlank String sourceAccountNumber,
            @NotBlank String destinationAccountNumber,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amountEgp
    ) {}

    public record OwnTransferResponse(
            String sourceAccountNumber,
            String destinationAccountNumber,
            BigDecimal amountEgp,
            BigDecimal feeEgp,
            BigDecimal sourceBalanceAfter,
            BigDecimal destinationBalanceAfter
    ) {}

    @PostMapping("/deposit")
    @ResponseStatus(HttpStatus.CREATED)
    public DepositResponse deposit(@Valid @RequestBody DepositRequest request) {
        Account account = depositService.deposit(request.accountNumber(), request.amountEgp());
        return new DepositResponse(
                account.getAccountNumber(),
                request.amountEgp().setScale(2, RoundingMode.HALF_EVEN),
                account.getBalance(),
                account.getCurrency()
        );
    }

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

    @PostMapping("/transfer-own")
    @ResponseStatus(HttpStatus.CREATED)
    public OwnTransferResponse transferOwn(@Valid @RequestBody OwnTransferRequest request) {
        OwnAccountTransferService.TransferResult result = ownAccountTransferService.transfer(
                request.sourceAccountNumber(),
                request.destinationAccountNumber(),
                request.amountEgp()
        );
        return new OwnTransferResponse(
                request.sourceAccountNumber(),
                request.destinationAccountNumber(),
                request.amountEgp().setScale(2, RoundingMode.HALF_EVEN),
                result.feeEgp(),
                result.sourceBalanceAfter(),
                result.destinationBalanceAfter()
        );
    }
}