package com.project.web.controller;

import com.project.simulation.DatasetEventProducer;
import com.project.simulation.ScenarioRunnerService;
import com.project.simulation.ScenarioStatus;
import com.project.web.dto.SimulationDtos.SimulationStartRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    private final DatasetEventProducer datasetProducer;
    private final ScenarioRunnerService scenarioRunner;

    public SimulationController(DatasetEventProducer datasetProducer, ScenarioRunnerService scenarioRunner) {
        this.datasetProducer = datasetProducer;
        this.scenarioRunner = scenarioRunner;
    }

    @PostMapping("/start")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> startSimulation(@RequestBody(required = false) SimulationStartRequest request) {
        Double rate = request != null ? request.getEventsPerSec() : null;
        Long seed = request != null ? request.getSeed() : null;
        datasetProducer.start(rate, seed);

        return ResponseEntity.ok(Map.of(
                "status", "STARTED",
                "eventsPerSec", datasetProducer.getCurrentEventsPerSec(),
                "emitted", datasetProducer.getEmittedCount()
        ));
    }

    @PostMapping("/pause")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> pauseSimulation() {
        datasetProducer.pause();
        return ResponseEntity.ok(Map.of("status", "PAUSED"));
    }

    @PostMapping("/resume")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> resumeSimulation() {
        datasetProducer.resume();
        return ResponseEntity.ok(Map.of("status", "RESUMED"));
    }

    @PostMapping("/stop")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> stopSimulation() {
        datasetProducer.stop();
        return ResponseEntity.ok(Map.of(
                "status", "STOPPED",
                "totalEmitted", datasetProducer.getEmittedCount()
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSimulationStatus() {
        Map<String, Object> map = new HashMap<>();
        map.put("running", datasetProducer.isRunning());
        map.put("paused", datasetProducer.isPaused());
        map.put("eventsPerSec", datasetProducer.getCurrentEventsPerSec());
        map.put("emittedCount", datasetProducer.getEmittedCount());
        return ResponseEntity.ok(map);
    }

    @GetMapping("/scenarios")
    public ResponseEntity<List<ScenarioStatus>> listScenarios() {
        return ResponseEntity.ok(scenarioRunner.listAvailableScenarios());
    }

    @PostMapping("/scenario/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ScenarioStatus> runScenario(@PathVariable String id) {
        return ResponseEntity.ok(scenarioRunner.runScenario(id));
    }

    @GetMapping("/scenario/{id}")
    public ResponseEntity<ScenarioStatus> getScenarioStatus(@PathVariable String id) {
        ScenarioStatus status = scenarioRunner.getScenarioStatus(id);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }
}
