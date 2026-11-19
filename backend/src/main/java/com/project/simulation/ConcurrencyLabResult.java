package com.project.simulation;

import com.project.os.ConcurrencyMode;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Map;

/**
 * Result data transfer object for Concurrency Lab execution.
 */
@Getter
@Builder
@ToString
public class ConcurrencyLabResult {
    private final String sku;
    private final int initialStock;
    private final int threadCount;
    private final int ordersPerThread;
    private final int totalOrders;
    private final ConcurrencyMode concurrencyMode;
    private final long artificialDelayMs;
    private final int expectedFinalStock;
    private final int actualFinalStock;
    private final long movementsCount;
    private final long lostUpdatesCount;
    private final long elapsedTimeMs;
    private final long lockContentionCount;
    private final double avgLockWaitTimeMs;
    private final double p95LockWaitTimeMs;
    private final Map<String, Integer> perThreadSuccess;
    private final Map<String, Integer> perThreadFailed;
    private final boolean consistent; // true if actualFinalStock == expectedFinalStock and lostUpdates == 0
}
