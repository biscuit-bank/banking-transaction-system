package com.depi.bank.service;

import com.depi.bank.repository.AccountRepository;
import com.depi.bank.repository.LedgerRepository;
import java.time.Clock;

public class FixedDepositService extends FixedDepositWithdrawalService {
    public FixedDepositService(AccountRepository accountRepository, LedgerRepository ledgerRepository) {
        super(accountRepository, ledgerRepository);
    }
    public FixedDepositService(AccountRepository accountRepository, LedgerRepository ledgerRepository, Clock clock) {
        super(accountRepository, ledgerRepository, clock);
    }
}
