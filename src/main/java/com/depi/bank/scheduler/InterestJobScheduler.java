package com.depi.bank.scheduler;

import com.depi.bank.service.InterestPostingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cron entry points for JOB-01 and JOB-02.
 *
 * <p>The trigger schedule is externalized in {@code application.yml}. All business-date logic is
 * delegated to {@link InterestPostingService}, which uses the injected {@code java.time.Clock}
 * (NFR-05), so the jobs remain deterministic and idempotent when retried.</p>
 */
@Component
public class InterestJobScheduler {

    private static final Logger log = LoggerFactory.getLogger(InterestJobScheduler.class);

    private final InterestPostingService interestPostingService;

    public InterestJobScheduler(InterestPostingService interestPostingService) {
        this.interestPostingService = interestPostingService;
    }

    /**
     * Runs on the first day of every calendar month (Cairo time).
     */
    @Scheduled(
            cron = "${bank.jobs.savings-interest-cron:0 0 0 1 * *}",
            zone = "${bank.jobs.zone:Africa/Cairo}")
    public void runMonthlySavingsInterestJob() {
        try {
            int credited = interestPostingService.runMonthlySavingsInterestJob();
            log.info("JOB-01 monthly savings interest completed: {} account(s) credited", credited);
        } catch (RuntimeException ex) {
            log.error("JOB-01 monthly savings interest failed; transaction rolled back", ex);
        }
    }

    /**
     * Runs daily so a Fixed Deposit is credited on its maturity date (or the next run after it).
     */
    @Scheduled(
            cron = "${bank.jobs.fixed-deposit-maturity-cron:0 5 0 * * *}",
            zone = "${bank.jobs.zone:Africa/Cairo}")
    public void runFixedDepositMaturityJob() {
        try {
            int credited = interestPostingService.runFixedDepositMaturityJob();
            log.info("JOB-02 Fixed Deposit maturity completed: {} deposit(s) credited", credited);
        } catch (RuntimeException ex) {
            log.error("JOB-02 Fixed Deposit maturity failed; transaction rolled back", ex);
        }
    }
}
