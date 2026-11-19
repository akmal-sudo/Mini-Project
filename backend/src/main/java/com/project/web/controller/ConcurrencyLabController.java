package com.project.web.controller;

import com.project.os.ConcurrencyMode;
import com.project.os.OsEngine;
import com.project.simulation.ConcurrencyLabResult;
import com.project.simulation.ConcurrencyLabService;
import com.project.web.dto.SimulationDtos.ConcurrencyModeChangeRequest;
import com.project.web.dto.SimulationDtos.LabRunRequest;
import com.project.web.dto.SimulationDtos.WorkerPoolResizeRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/lab")
public class ConcurrencyLabController {

    private final ConcurrencyLabService labService;
    private final OsEngine osEngine;

    private volatile ConcurrencyLabResult lastResult;

    public ConcurrencyLabController(ConcurrencyLabService labService, OsEngine osEngine) {
        this.labService = labService;
        this.osEngine = osEngine;
    }

    @PostMapping("/run")
    public ResponseEntity<ConcurrencyLabResult> runLabTest(@Valid @RequestBody LabRunRequest request) {
        ConcurrencyMode mode = ConcurrencyMode.valueOf(request.getConcurrencyMode().toUpperCase());
        ConcurrencyLabResult result = labService.runLabTest(
                request.getSku(),
                request.getInitialStock(),
                request.getThreadCount(),
                request.getOrdersPerThread(),
                mode,
                request.getArtificialDelayMs()
        );
        this.lastResult = result;
        return ResponseEntity.ok(result);
    }

    @GetMapping("/last-result")
    public ResponseEntity<ConcurrencyLabResult> getLastResult() {
        if (lastResult == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(lastResult);
    }

    @PostMapping("/mode")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> setConcurrencyMode(@Valid @RequestBody ConcurrencyModeChangeRequest request) {
        ConcurrencyMode mode = ConcurrencyMode.valueOf(request.getMode().toUpperCase());
        osEngine.setConcurrencyMode(mode);
        return ResponseEntity.ok(Map.of("concurrencyMode", mode.name()));
    }

    @PostMapping("/workers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Integer>> setWorkerPoolSize(@Valid @RequestBody WorkerPoolResizeRequest request) {
        osEngine.setWorkerPoolSize(request.getWorkerPoolSize());
        return ResponseEntity.ok(Map.of("workerPoolSize", osEngine.getWorkerPoolSize()));
    }
}
