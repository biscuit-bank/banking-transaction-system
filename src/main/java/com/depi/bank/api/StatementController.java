package com.depi.bank.api;

import com.depi.bank.domain.Transaction;
import com.depi.bank.service.CsvExportService;
import com.depi.bank.service.StatementService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/accounts")
public class StatementController {

    private final StatementService statementService;
    private final CsvExportService csvExportService;

    public StatementController(StatementService statementService, CsvExportService csvExportService) {
        this.statementService = statementService;
        this.csvExportService = csvExportService;
    }

    @GetMapping("/{accountNumber}/statement")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Page<Transaction>> getStatement(
            @PathVariable String accountNumber,
            @RequestParam(name = "start_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "end_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {

        String email = (authentication != null) ? authentication.getName() : null;
        return ResponseEntity.ok(
                statementService.getAccountStatement(accountNumber, email, startDate, endDate, type, page, size)
        );
    }

    @GetMapping("/{accountNumber}/statement/export")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<byte[]> exportStatementCsv(
            @PathVariable String accountNumber,
            @RequestParam(name = "start_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "end_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "type", required = false) String type,
            Authentication authentication) {

        String email = (authentication != null) ? authentication.getName() : null;
        byte[] csvBytes = csvExportService.exportStatementToUtf8Csv(accountNumber, email, startDate, endDate, type);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"statement_" + accountNumber + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvBytes);
    }
}
