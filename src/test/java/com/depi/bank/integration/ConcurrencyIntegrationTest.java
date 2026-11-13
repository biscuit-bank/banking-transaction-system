package com.depi.bank.integration;

import com.depi.bank.domain.account.Account;
import com.depi.bank.domain.account.LedgerEntry;
import com.depi.bank.repository.AccountRepository;
import com.depi.bank.repository.LedgerRepository;
import com.depi.bank.service.AccountOpeningService;
import com.depi.bank.service.CheckingWithdrawalService;
import com.depi.bank.service.OwnAccountTransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrencyIntegrationTest {

    @Autowired
    private AccountOpeningService accountOpeningService;

    @Autowired
    private CheckingWithdrawalService checkingWithdrawalService;

    @Autowired
    private OwnAccountTransferService ownAccountTransferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LedgerRepository ledgerRepository;

    @Test
    void tenConcurrentWithdrawals_noRaceAndConsistentLedger() throws Exception {
        UUID customerId = UUID.randomUUID();
        Account account = accountOpeningService.openCheckingAccount(customerId, BigDecimal.ZERO.setScale(2));
        String accountNumber = account.getAccountNumber();

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Throwable> errors = new ArrayList<>();
        AtomicInteger successes = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    checkingWithdrawalService.withdraw(accountNumber, new BigDecimal("50.00"));
                    successes.incrementAndGet();
                } catch (Throwable t) {
                    synchronized (errors) {
                        errors.add(t);
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS));
        executor.shutdown();
        assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));

        assertTrue(errors.isEmpty(), "Unexpected errors: " + errors);
        assertEquals(threads, successes.get());

        Account updated = accountRepository.findByAccountNumber(accountNumber).orElseThrow();
        assertEquals(0, updated.getBalance().compareTo(new BigDecimal("-500.00")));

        long ledgerCount = ledgerRepository.findByAccountIdAndTypeInAndCreatedAtBetween(
                updated.getId(),
                java.util.EnumSet.allOf(com.depi.bank.domain.account.TransactionType.class),
                java.time.Instant.EPOCH,
                java.time.Instant.now().plusSeconds(3600)).size();
        assertEquals(threads, ledgerCount);
    }

    @Test
    void tenConcurrentTransfers_noRaceAndConsistentLedger() throws Exception {
        UUID customerId = UUID.randomUUID();
        Account source = accountOpeningService.openCheckingAccount(customerId, new BigDecimal("200.00"));
        Account destination = accountOpeningService.openCheckingAccount(customerId, BigDecimal.ZERO.setScale(2));
        String srcNo = source.getAccountNumber();
        String dstNo = destination.getAccountNumber();

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Throwable> errors = new ArrayList<>();
        AtomicInteger successes = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    ownAccountTransferService.transfer(srcNo, dstNo, new BigDecimal("10.00"));
                    successes.incrementAndGet();
                } catch (Throwable t) {
                    synchronized (errors) {
                        errors.add(t);
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS));
        executor.shutdown();
        assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));

        assertTrue(errors.isEmpty(), "Unexpected errors: " + errors);
        assertEquals(threads, successes.get());

        Account srcUpdated = accountRepository.findByAccountNumber(srcNo).orElseThrow();
        Account dstUpdated = accountRepository.findByAccountNumber(dstNo).orElseThrow();
        assertEquals(0, srcUpdated.getBalance().compareTo(new BigDecimal("100.00")));
        assertEquals(0, dstUpdated.getBalance().compareTo(new BigDecimal("100.00")));
    }
}
