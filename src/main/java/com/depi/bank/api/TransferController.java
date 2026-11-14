package com.depi.bank.api;

import com.depi.bank.domain.IdempotencyKey;
import com.depi.bank.domain.LuhnAccountNumberService;
import com.depi.bank.domain.account.Account;
import com.depi.bank.exception.BankException;
import com.depi.bank.exception.ErrorCode;
import com.depi.bank.repository.AccountRepository;
import com.depi.bank.repository.IdempotencyKeyRepository;
import com.depi.bank.service.InterCustomerTransferService;
import com.depi.bank.service.OwnAccountTransferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final OwnAccountTransferService ownAccountTransferService;
    private final InterCustomerTransferService interCustomerTransferService;
    private final AccountRepository accountRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final LuhnAccountNumberService luhnService;

    public TransferController(OwnAccountTransferService ownAccountTransferService,
                              InterCustomerTransferService interCustomerTransferService,
                              AccountRepository accountRepository,
                              IdempotencyKeyRepository idempotencyKeyRepository,
                              LuhnAccountNumberService luhnService) {
        this.ownAccountTransferService = ownAccountTransferService;
        this.interCustomerTransferService = interCustomerTransferService;
        this.accountRepository = accountRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.luhnService = luhnService;
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

    public record QuoteResponse(
            String sourceAccountNumber,
            String destinationAccountNumber,
            BigDecimal amountEgp,
            BigDecimal principalEgp,
            BigDecimal feeEgp,
            BigDecimal totalDebitEgp,
            BigDecimal totalDebit
    ) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OwnTransferResponse transfer(@Valid @RequestBody OwnTransferRequest request,
                                       @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return execute(request, idempotencyKey);
    }

    @PostMapping("/own")
    @ResponseStatus(HttpStatus.CREATED)
    public OwnTransferResponse transferOwn(@Valid @RequestBody OwnTransferRequest request,
                                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return execute(request, idempotencyKey);
    }

    @PostMapping("/quote")
    @ResponseStatus(HttpStatus.OK)
    public QuoteResponse quote(@Valid @RequestBody OwnTransferRequest request) {
        if (!luhnService.isValid(request.sourceAccountNumber()) || !luhnService.isValid(request.destinationAccountNumber())) {
            throw new BankException(ErrorCode.INVALID_ACCOUNT_NUMBER);
        }
        Account source = accountRepository.findByAccountNumber(request.sourceAccountNumber())
                .orElseThrow(() -> new BankException(ErrorCode.INVALID_ACCOUNT_NUMBER));
        Account destination = accountRepository.findByAccountNumber(request.destinationAccountNumber())
                .orElseThrow(() -> new BankException(ErrorCode.INVALID_ACCOUNT_NUMBER));

        BigDecimal amount = request.amountEgp().setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal fee;
        if (source.getCustomerId().equals(destination.getCustomerId())) {
            fee = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
        } else {
            fee = interCustomerTransferService.calculateFee(amount);
        }
        BigDecimal totalDebit = amount.add(fee).setScale(2, RoundingMode.HALF_EVEN);

        return new QuoteResponse(
                request.sourceAccountNumber(),
                request.destinationAccountNumber(),
                amount,
                amount,
                fee,
                totalDebit,
                totalDebit
        );
    }

    private OwnTransferResponse execute(OwnTransferRequest request, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = idempotencyKeyRepository.findByKey(idempotencyKey);
            if (existing.isPresent()) {
                IdempotencyKey stored = existing.get();
                BigDecimal requestAmount = request.amountEgp().setScale(2, RoundingMode.HALF_EVEN);
                if (!stored.getSourceAccountNumber().equals(request.sourceAccountNumber())
                        || !stored.getDestinationAccountNumber().equals(request.destinationAccountNumber())
                        || stored.getAmountEgp().compareTo(requestAmount) != 0) {
                    throw new BankException(ErrorCode.IDEMPOTENCY_CONFLICT);
                }
                return new OwnTransferResponse(
                        stored.getSourceAccountNumber(),
                        stored.getDestinationAccountNumber(),
                        stored.getAmountEgp(),
                        stored.getFeeEgp(),
                        stored.getSourceBalanceAfter(),
                        stored.getDestinationBalanceAfter()
                );
            }
        }

        Account source = accountRepository.findByAccountNumber(request.sourceAccountNumber())
                .orElseThrow(() -> new BankException(ErrorCode.ACCOUNT_NOT_FOUND));
        Account destination = accountRepository.findByAccountNumber(request.destinationAccountNumber())
                .orElseThrow(() -> new BankException(ErrorCode.ACCOUNT_NOT_FOUND));

        OwnAccountTransferService.TransferResult result;
        if (source.getCustomerId().equals(destination.getCustomerId())) {
            result = ownAccountTransferService.transfer(
                    request.sourceAccountNumber(),
                    request.destinationAccountNumber(),
                    request.amountEgp());
        } else {
            InterCustomerTransferService.TransferResult ic = interCustomerTransferService.transfer(
                    request.sourceAccountNumber(),
                    request.destinationAccountNumber(),
                    request.amountEgp());
            result = new OwnAccountTransferService.TransferResult(
                    ic.sourceBalanceAfter(),
                    ic.destinationBalanceAfter(),
                    ic.feeEgp());
        }

        OwnTransferResponse response = new OwnTransferResponse(
                request.sourceAccountNumber(),
                request.destinationAccountNumber(),
                request.amountEgp().setScale(2, RoundingMode.HALF_EVEN),
                result.feeEgp(),
                result.sourceBalanceAfter(),
                result.destinationBalanceAfter());

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            IdempotencyKey stored = new IdempotencyKey();
            stored.setKey(idempotencyKey);
            // customerId derived from source account owner
            stored.setCustomerId(source.getCustomerId());
            stored.setSourceAccountNumber(request.sourceAccountNumber());
            stored.setDestinationAccountNumber(request.destinationAccountNumber());
            stored.setAmountEgp(request.amountEgp().setScale(2, RoundingMode.HALF_EVEN));
            stored.setFeeEgp(result.feeEgp());
            stored.setSourceBalanceAfter(result.sourceBalanceAfter());
            stored.setDestinationBalanceAfter(result.destinationBalanceAfter());
            stored.setCreatedAt(Instant.now());
            stored.setExpiresAt(Instant.now().plusSeconds(24 * 3600));
            idempotencyKeyRepository.save(stored);
        }

        return response;
    }
}
