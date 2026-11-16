package com.depi.bank.api;

import com.depi.bank.domain.Account;
import com.depi.bank.service.AccountFreezeService;
import com.depi.bank.service.AdminDashboardService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminAccountController {

    private final AccountFreezeService accountFreezeService;
    private final AdminDashboardService adminDashboardService;

    public AdminAccountController(AccountFreezeService accountFreezeService,
                                  AdminDashboardService adminDashboardService) {
        this.accountFreezeService = accountFreezeService;
        this.adminDashboardService = adminDashboardService;
    }

    @PostMapping("/accounts/{accountNumber}/freeze")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Account> freezeAccount(@PathVariable String accountNumber,
                                                 @RequestBody(required = false) Map<String, String> body,
                                                 Authentication authentication,
                                                 HttpServletRequest request) {
        String reason = (body != null) ? body.get("reason") : null;
        String adminIdentifier = (authentication != null) ? authentication.getName() : "admin";
        return ResponseEntity.ok(
                accountFreezeService.freezeAccount(accountNumber, reason, adminIdentifier, request.getRemoteAddr())
        );
    }

    @PostMapping("/accounts/{accountNumber}/unfreeze")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Account> unfreezeAccount(@PathVariable String accountNumber,
                                                   @RequestBody(required = false) Map<String, String> body,
                                                   Authentication authentication,
                                                   HttpServletRequest request) {
        String reason = (body != null) ? body.get("reason") : null;
        String adminIdentifier = (authentication != null) ? authentication.getName() : "admin";
        return ResponseEntity.ok(
                accountFreezeService.unfreezeAccount(accountNumber, reason, adminIdentifier, request.getRemoteAddr())
        );
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getDashboardMetrics() {
        return ResponseEntity.ok(adminDashboardService.getDashboardMetrics());
    }

    @GetMapping("/customers/search")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> searchCustomers(@RequestParam(required = false) String query) {
        return ResponseEntity.ok(adminDashboardService.searchCustomers(query));
    }

    @GetMapping("/accounts/search")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Account>> searchAccounts(
            @RequestParam(required = false) String accountNumber,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) BigDecimal minBalance,
            @RequestParam(required = false) BigDecimal maxBalance) {
        return ResponseEntity.ok(
                adminDashboardService.searchAccounts(accountNumber, type, status, minBalance, maxBalance)
        );
    }

    @GetMapping("/reports/richest-customer")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getRichestCustomer() {
        return ResponseEntity.ok(adminDashboardService.getRichestCustomers());
    }
}
