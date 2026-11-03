package com.depi.bank.api;

import com.depi.bank.domain.AuditLog;
import com.depi.bank.repository.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AdminAuditController {

    private static final ZoneId CAIRO_ZONE = ZoneId.of("Africa/Cairo");
    private final AuditLogRepository auditLogRepository;

    public AdminAuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public ResponseEntity<Page<AuditLog>> queryAuditLogs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String actionType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        int boundedSize = Math.min(Math.max(size, 1), 100);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), boundedSize, Sort.by(Sort.Direction.DESC, "createdAt", "id"));

        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startDate != null) {
                Instant startInstant = startDate.atStartOfDay(CAIRO_ZONE).toInstant();
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startInstant));
            }
            if (endDate != null) {
                Instant endInstant = endDate.plusDays(1).atStartOfDay(CAIRO_ZONE).toInstant();
                predicates.add(cb.lessThan(root.get("createdAt"), endInstant));
            }
            if (actor != null && !actor.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("actorIdentifier")), "%" + actor.trim().toLowerCase() + "%"));
            }
            if (actionType != null && !actionType.isBlank() && !"ALL".equalsIgnoreCase(actionType.trim())) {
                predicates.add(cb.equal(cb.upper(root.get("actionCode")), actionType.trim().toUpperCase()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return ResponseEntity.ok(auditLogRepository.findAll(spec, pageable));
    }
}