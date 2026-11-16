package com.depi.bank.api;

import com.depi.bank.service.InterestJobService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminJobController {

    private final InterestJobService interestJobService;

    public AdminJobController(InterestJobService interestJobService) {
        this.interestJobService = interestJobService;
    }

    @PostMapping({"/jobs/monthly-interest", "/jobs/run-monthly-interest"})
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity> triggerMonthlyInterestJob(Authentication authentication,
                                                                         HttpServletRequest request) {
        String adminIdentifier = (authentication != null) ? authentication.getName() : "admin";
        return ResponseEntity.ok(
                interestJobService.runMonthlyInterestAndFdMaturity(adminIdentifier, request.getRemoteAddr())
        );
    }
}