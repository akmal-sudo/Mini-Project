package com.project.os;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages fine-grained per-SKU ReentrantLocks with contention telemetry.
 *
 * LOCK-ORDERING SPECIFICATION (guarantees deadlock freedom):
 * 1. Acquire the application-level per-SKU lock first.
 * 2. Acquire the database row lock (SELECT ... FOR UPDATE) second.
 * 3. Never hold more than one SKU lock concurrently on a single thread.
 * 4. Always use bounded tryLock timeouts to guarantee no permanent deadlock.
 */
@Component
public class SkuLockManager {

    private static final Logger log = LoggerFactory.getLogger(SkuLockManager.class);
    private static final int MAX_SAMPLES = 5000;

    private final ConcurrentHashMap<String, ReentrantLock> skuLocks = new ConcurrentHashMap<>();
    private final AtomicLong contentionCount = new AtomicLong(0);
    private final AtomicLong totalAcquisitions = new AtomicLong(0);
    private final ConcurrentLinkedDeque<Long> waitTimeNanosSamples = new ConcurrentLinkedDeque<>();

    public ReentrantLock getLockForSku(String sku) {
        return skuLocks.computeIfAbsent(sku, k -> new ReentrantLock(true)); // fair lock
    }

    /**
     * Attempts to acquire the per-SKU lock with timeout and measures wait time.
     */
    public boolean tryLock(String sku, long timeout, TimeUnit unit) throws InterruptedException {
        ReentrantLock lock = getLockForSku(sku);
        if (lock.isLocked()) {
            contentionCount.incrementAndGet();
        }

        long startNanos = System.nanoTime();
        boolean acquired = lock.tryLock(timeout, unit);
        long elapsedNanos = System.nanoTime() - startNanos;

        if (acquired) {
            totalAcquisitions.incrementAndGet();
            recordWaitSample(elapsedNanos);
        } else {
            log.warn("Failed to acquire lock for SKU {} within {} {}", sku, timeout, unit);
        }

        return acquired;
    }

    /**
     * Releases the per-SKU lock if held by current thread.
     */
    public void unlock(String sku) {
        ReentrantLock lock = skuLocks.get(sku);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    private void recordWaitSample(long nanos) {
        waitTimeNanosSamples.addLast(nanos);
        while (waitTimeNanosSamples.size() > MAX_SAMPLES) {
            waitTimeNanosSamples.pollFirst();
        }
    }

    public long getContentionCount() {
        return contentionCount.get();
    }

    public long getTotalAcquisitions() {
        return totalAcquisitions.get();
    }

    public double getAvgLockWaitTimeMs() {
        List<Long> samples = new ArrayList<>(waitTimeNanosSamples);
        if (samples.isEmpty()) return 0.0;
        long sum = 0;
        for (long s : samples) sum += s;
        return (sum / (double) samples.size()) / 1_000_000.0;
    }

    public double getP95LockWaitTimeMs() {
        List<Long> samples = new ArrayList<>(waitTimeNanosSamples);
        if (samples.isEmpty()) return 0.0;
        Collections.sort(samples);
        int idx = (int) Math.min(samples.size() - 1, Math.floor(samples.size() * 0.95));
        return samples.get(idx) / 1_000_000.0;
    }

    public void resetMetrics() {
        contentionCount.set(0);
        totalAcquisitions.set(0);
        waitTimeNanosSamples.clear();
    }
}
