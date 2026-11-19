package com.project.web.controller;

import com.project.alert.AlertService;
import com.project.dbms.entity.Alert;
import com.project.dbms.repository.AlertRepository;
import com.project.web.security.UserPrincipal;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Stock alerts describe MANAGER stock, so vendors cannot read them. */
@RestController
@RequestMapping("/api/alerts")
@PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE_MANAGER')")
public class AlertController {

    private final AlertRepository alertRepository;
    private final AlertService alertService;

    public AlertController(AlertRepository alertRepository, AlertService alertService) {
        this.alertRepository = alertRepository;
        this.alertService = alertService;
    }

    @GetMapping
    public ResponseEntity<List<Alert>> getAlerts(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        if (status != null && !status.isBlank()) {
            return ResponseEntity.ok(alertRepository.findByStatusOrderByCreatedAtDesc(status.toUpperCase()));
        }

        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(alertRepository.findAll(pageRequest).getContent());
    }

    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<Alert> acknowledgeAlert(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        Integer userId = principal != null ? principal.getUserId() : null;
        return ResponseEntity.ok(alertService.acknowledgeAlert(id, userId));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<Alert> resolveAlert(@PathVariable Long id) {
        return ResponseEntity.ok(alertService.resolveAlert(id));
    }

    @GetMapping("/latency")
    public ResponseEntity<AlertService.LatencyStats> getLatencyStats() {
        return ResponseEntity.ok(alertService.getDetectionLatencyStats());
    }
}
