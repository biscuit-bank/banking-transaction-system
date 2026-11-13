package com.depi.bank.api;

import com.depi.bank.service.AuditLogService;
import com.depi.bank.service.InterestPostingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative manual trigger for JOB-01 / JOB-02 (ADM-06).
 *
 * <p>The endpoint is idempotent: invoking it repeatedly in the same calendar month returns a
 * result with zero newly credited accounts and writes no duplicate ledger rows.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/jobs")
public class AdminJobController {

    private static final String JOB_TARGET = "INTEREST_JOBS";

    private final InterestPostingService interestPostingService;
    private final AuditLogService auditLogService;

    public AdminJobController(InterestPostingService interestPostingService,
                              AuditLogService auditLogService) {
        this.interestPostingService = interestPostingService;
        this.auditLogService = auditLogService;
    }

    public record InterestJobResponse(
            int savingsAccountsCredited,
            int fixedDepositsCredited,
            int totalCredited
    ) {}

    @PostMapping("/monthly-interest")
    public ResponseEntity<InterestJobResponse> runMonthlyInterestJobs(
            Authentication authentication,
            HttpServletRequest request) {

        InterestPostingService.InterestJobResult result =
                interestPostingService.runMonthlyInterestJobs();

        String actor = authentication != null ? authentication.getName() : "SYSTEM";
        auditLogService.logSuccess(
                null,
                actor,
                "JOB_TRIGGER",
                JOB_TARGET,
                "Monthly interest job triggered: savings=" + result.savingsAccountsCredited()
                        + ", fixedDeposits=" + result.fixedDepositsCredited(),
                request.getRemoteAddr());

        return ResponseEntity.ok(new InterestJobResponse(
                result.savingsAccountsCredited(),
                result.fixedDepositsCredited(),
                result.totalCredited()));
    }
}
