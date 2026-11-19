package com.project.web.controller;

import com.project.web.service.EnginesHealthService;
import com.project.web.service.EnginesHealthService.CombinedEnginesHealth;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/engines")
public class EnginesHealthController {

    private final EnginesHealthService healthService;

    public EnginesHealthController(EnginesHealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    public ResponseEntity<CombinedEnginesHealth> getHealth() {
        return ResponseEntity.ok(healthService.getCombinedHealth());
    }
}
