package com.project.web.controller;

import com.project.dbms.entity.AuditLog;
import com.project.dbms.repository.AuditLogRepository;
import com.project.dbms.service.ConsistencyCheckService;
import com.project.dbms.service.ConsistencyCheckService.ConsistencyReport;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final ConsistencyCheckService consistencyCheckService;
    private final AuditLogRepository auditLogRepository;

    public AuditController(
            ConsistencyCheckService consistencyCheckService,
            AuditLogRepository auditLogRepository) {
        this.consistencyCheckService = consistencyCheckService;
        this.auditLogRepository = auditLogRepository;
    }

    @PostMapping("/consistency-check")
    public ResponseEntity<ConsistencyReport> runConsistencyCheck() {
        return ResponseEntity.ok(consistencyCheckService.runConsistencyCheck());
    }

    @GetMapping("/logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AuditLog>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(auditLogRepository.findAll(pageRequest).getContent());
    }
}
