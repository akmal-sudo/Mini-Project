package com.project.os;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Map;

/**
 * Live snapshot of OS Engine runtime concurrency metrics.
 */
@Getter
@Builder
@ToString
public class OsMetricsSnapshot {
    private final int queueDepth;
    private final int queueCapacity;
    private final long producedCount;
    private final long processedCount;
    private final long failedCount;
    private final long rejectedOverflowCount;
    private final int activeWorkerCount;
    private final int workerPoolSize;
    private final Map<String, Long> perThreadProcessedCounts;
    private final long lockContentionCount;
    private final double avgLockWaitTimeMs;
    private final double p95LockWaitTimeMs;
    private final ConcurrencyMode concurrencyMode;
    private final long artificialDelayMs;
}
