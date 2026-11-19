package com.project.dsa;

/**
 * Interface boundary for DSA Engine.
 * Exposes the in-memory data structures and real-time telemetry snapshots.
 */
public interface DsaEngine {

    KMPMatcher getKmpMatcher();

    InventoryCache getInventoryCache();

    EventDeduplicator getDeduplicator();

    EventFifoQueue<Object> getOrderQueue();

    EventFifoQueue<Object> getLogQueue();

    SlidingEventWindow<Object> getSlidingWindow();

    FrequencyTracker getFrequencyTracker();

    ReorderPriorityHeap getPriorityHeap();

    TopKTracker getTopKTracker();

    DsaMetricsSnapshot getMetricsSnapshot();
}
