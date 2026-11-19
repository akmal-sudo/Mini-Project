package com.project.dsa;

import lombok.Builder;
import lombok.Getter;

/**
 * Immutable telemetry snapshot of all DSA in-memory data structures.
 */
@Getter
@Builder
public class DsaMetricsSnapshot {
    private final int orderQueueSize;
    private final int logQueueSize;
    private final int cacheSize;
    private final int deduplicatorSetSize;
    private final long duplicatesRejectedCount;
    private final int slidingWindowCount;
    private final int priorityHeapSize;
    private final int topKHeapSize;
    private final long kmpScansCount;
    private final long kmpMatchesCount;
    private final long kmpComparisonsCount;
}
