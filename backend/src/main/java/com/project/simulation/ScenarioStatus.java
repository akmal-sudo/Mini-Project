package com.project.simulation;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Map;

/**
 * Scenario status and execution results DTO.
 */
@Getter
@Builder
@ToString
public class ScenarioStatus {
    private final String scenarioId;
    private final String name;
    private final String status; // IDLE, RUNNING, COMPLETED, FAILED
    private final String description;
    private final Instant startTime;
    private final Instant endTime;
    private final long totalEvents;
    private final long processedEvents;
    private final Map<String, Object> results;
    private final String message;
}
