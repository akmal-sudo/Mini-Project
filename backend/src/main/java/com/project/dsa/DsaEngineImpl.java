package com.project.dsa;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Concrete implementation of the DSA Engine.
 * Thread-safe singleton providing high-performance in-memory indexing,
 * windowing, caching, signature detection, and priority queues.
 */
@Service
public class DsaEngineImpl implements DsaEngine {

    private final KMPMatcher kmpMatcher;
    private final InventoryCache inventoryCache;
    private final EventDeduplicator deduplicator;
    private final EventFifoQueue<Object> orderQueue;
    private final EventFifoQueue<Object> logQueue;
    private final SlidingEventWindow<Object> slidingWindow;
    private final FrequencyTracker frequencyTracker;
    private final ReorderPriorityHeap priorityHeap;
    private final TopKTracker topKTracker;

    public DsaEngineImpl(
            @Value("${app.os.queue-capacity:5000}") int orderQueueCapacity,
            @Value("${app.dsa.sliding-window-count:100}") int windowCount,
            @Value("${app.dsa.sliding-window-seconds:60}") int windowSeconds,
            @Value("${app.dsa.top-k-size:5}") int topKSize) {
        this.kmpMatcher = new KMPMatcher();
        this.inventoryCache = new InventoryCache();
        this.deduplicator = new EventDeduplicator();
        this.orderQueue = new EventFifoQueue<>(orderQueueCapacity);
        this.logQueue = new EventFifoQueue<>(orderQueueCapacity);
        this.slidingWindow = new SlidingEventWindow<>(windowCount, Duration.ofSeconds(windowSeconds));
        this.frequencyTracker = new FrequencyTracker();
        this.priorityHeap = new ReorderPriorityHeap();
        this.topKTracker = new TopKTracker(topKSize);
    }

    @Override
    public KMPMatcher getKmpMatcher() {
        return kmpMatcher;
    }

    @Override
    public InventoryCache getInventoryCache() {
        return inventoryCache;
    }

    @Override
    public EventDeduplicator getDeduplicator() {
        return deduplicator;
    }

    @Override
    public EventFifoQueue<Object> getOrderQueue() {
        return orderQueue;
    }

    @Override
    public EventFifoQueue<Object> getLogQueue() {
        return logQueue;
    }

    @Override
    public SlidingEventWindow<Object> getSlidingWindow() {
        return slidingWindow;
    }

    @Override
    public FrequencyTracker getFrequencyTracker() {
        return frequencyTracker;
    }

    @Override
    public ReorderPriorityHeap getPriorityHeap() {
        return priorityHeap;
    }

    @Override
    public TopKTracker getTopKTracker() {
        return topKTracker;
    }

    @Override
    public DsaMetricsSnapshot getMetricsSnapshot() {
        return DsaMetricsSnapshot.builder()
                .orderQueueSize(orderQueue.size())
                .logQueueSize(logQueue.size())
                .cacheSize(inventoryCache.size())
                .deduplicatorSetSize(deduplicator.size())
                .duplicatesRejectedCount(deduplicator.getDuplicatesRejectedCount())
                .slidingWindowCount(slidingWindow.size())
                .priorityHeapSize(priorityHeap.size())
                .topKHeapSize(topKTracker.size())
                .kmpScansCount(kmpMatcher.getScansCount())
                .kmpMatchesCount(kmpMatcher.getMatchesCount())
                .kmpComparisonsCount(kmpMatcher.getComparisonsCount())
                .build();
    }
}
