package com.depi.bank.service;

import com.depi.bank.repository.AccountRepository;
import com.depi.bank.repository.LedgerRepository;
import java.math.BigDecimal;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InstaPayTransferService extends InterCustomerTransferService {
    @Autowired
    public InstaPayTransferService(AccountRepository accountRepository, LedgerRepository ledgerRepository) {
        super(accountRepository, ledgerRepository);
    }
    public InstaPayTransferService(AccountRepository accountRepository, LedgerRepository ledgerRepository, Clock clock) {
        super(accountRepository, ledgerRepository, clock);
    }
    public InstaPayTransferService(AccountRepository accountRepository, LedgerRepository ledgerRepository, Clock clock,
                BigDecimal maxSingleTxnEgp, BigDecimal maxDailyDebitEgp, BigDecimal maxMonthlyDebitEgp) {
        super(accountRepository, ledgerRepository, clock, maxSingleTxnEgp, maxDailyDebitEgp, maxMonthlyDebitEgp);
    }
    public InstaPayTransferService(AccountRepository accountRepository, LedgerRepository ledgerRepository, Clock clock,
                BigDecimal maxSingleTxnEgp, BigDecimal maxDailyDebitEgp,
                BigDecimal maxMonthlyDebitEgp, BigDecimal checkingOverdraftLimitEgp) {
        super(accountRepository, ledgerRepository, clock, maxSingleTxnEgp, maxDailyDebitEgp, maxMonthlyDebitEgp, checkingOverdraftLimitEgp);
    }
    public InstaPayTransferService(AccountRepository accountRepository, LedgerRepository ledgerRepository,
                BigDecimal maxSingleTxnEgp, BigDecimal maxDailyDebitEgp, BigDecimal maxMonthlyDebitEgp) {
        super(accountRepository, ledgerRepository, maxSingleTxnEgp, maxDailyDebitEgp, maxMonthlyDebitEgp);
    }
    public InstaPayTransferService(AccountRepository accountRepository, LedgerRepository ledgerRepository,
                BigDecimal maxSingleTxnEgp, BigDecimal maxDailyDebitEgp,
                BigDecimal maxMonthlyDebitEgp, BigDecimal checkingOverdraftLimitEgp) {
        super(accountRepository, ledgerRepository, maxSingleTxnEgp, maxDailyDebitEgp, maxMonthlyDebitEgp, checkingOverdraftLimitEgp);
    }
}
