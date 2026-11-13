package com.depi.bank.api;

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

/**
 * US-14: zero-fee transfer between two accounts owned by the same customer.
 *
 * <p>Both {@code POST /api/v1/transfers} and {@code POST /api/v1/transfers/own} are
 * supported. The operation writes exactly two ledger rows and no FEE row.</p>
 */
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final OwnAccountTransferService ownAccountTransferService;

    public TransferController(OwnAccountTransferService ownAccountTransferService) {
        this.ownAccountTransferService = ownAccountTransferService;
    }

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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OwnTransferResponse transfer(@Valid @RequestBody OwnTransferRequest request) {
        return execute(request);
    }

    @PostMapping("/own")
    @ResponseStatus(HttpStatus.CREATED)
    public OwnTransferResponse transferOwn(@Valid @RequestBody OwnTransferRequest request) {
        return execute(request);
    }

    private OwnTransferResponse execute(OwnTransferRequest request) {
        OwnAccountTransferService.TransferResult result = ownAccountTransferService.transfer(
                request.sourceAccountNumber(),
                request.destinationAccountNumber(),
                request.amountEgp());

        return new OwnTransferResponse(
                request.sourceAccountNumber(),
                request.destinationAccountNumber(),
                request.amountEgp().setScale(2, RoundingMode.HALF_EVEN),
                result.feeEgp(),
                result.sourceBalanceAfter(),
                result.destinationBalanceAfter());
    }
}
