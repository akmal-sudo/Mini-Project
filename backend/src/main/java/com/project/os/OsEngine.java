package com.project.os;

/**
 * Interface for OS Concurrency Engine managing producer-consumer pipelines,
 * worker pools, backpressure policies, and inventory synchronization modes.
 */
public interface OsEngine {

    /**
     * Submits an event to the internal bounded queue under the backpressure policy.
     */
    boolean submitEvent(StockEvent event);

    /**
     * Synchronously processes a stock event through the critical section.
     */
    ExecutionResult processStockEventSync(StockEvent event);

    /**
     * Retrieves an immutable point-in-time metrics snapshot.
     */
    OsMetricsSnapshot getMetricsSnapshot();

    /**
     * Dynamically resizes the background worker pool at runtime.
     */
    void setWorkerPoolSize(int newSize);

    /**
     * Gets current worker pool size.
     */
    int getWorkerPoolSize();

    /**
     * Changes the inventory synchronization mode at runtime.
     */
    void setConcurrencyMode(ConcurrencyMode mode);

    /**
     * Gets current active concurrency strategy.
     */
    ConcurrencyMode getConcurrencyMode();

    /**
     * Sets artificial delay in milliseconds (used in NONE mode to widen race window).
     */
    void setArtificialDelayMs(long delayMs);

    /**
     * Gets current artificial delay.
     */
    long getArtificialDelayMs();

    /**
     * Gracefully stops the worker threads.
     */
    void shutdown();

    /**
     * Resets counters and telemetry.
     */
    void resetMetrics();
}
