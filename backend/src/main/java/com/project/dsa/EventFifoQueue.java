package com.project.dsa;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe FIFO Queue for decoupling event ingestion from processing and analysis.
 *
 * @param <T> Event type
 */
public class EventFifoQueue<T> {

    private final BlockingQueue<T> queue;
    private final int capacity;
    private final AtomicLong enqueuedCount = new AtomicLong(0);
    private final AtomicLong dequeuedCount = new AtomicLong(0);
    private final AtomicLong droppedCount = new AtomicLong(0);

    public EventFifoQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    public boolean offer(T item) {
        if (item == null) return false;
        boolean added = queue.offer(item);
        if (added) {
            enqueuedCount.incrementAndGet();
        } else {
            droppedCount.incrementAndGet();
        }
        return added;
    }

    public boolean offer(T item, long timeout, TimeUnit unit) throws InterruptedException {
        if (item == null) return false;
        boolean added = queue.offer(item, timeout, unit);
        if (added) {
            enqueuedCount.incrementAndGet();
        } else {
            droppedCount.incrementAndGet();
        }
        return added;
    }

    public Optional<T> poll() {
        T item = queue.poll();
        if (item != null) {
            dequeuedCount.incrementAndGet();
        }
        return Optional.ofNullable(item);
    }

    public Optional<T> poll(long timeout, TimeUnit unit) throws InterruptedException {
        T item = queue.poll(timeout, unit);
        if (item != null) {
            dequeuedCount.incrementAndGet();
        }
        return Optional.ofNullable(item);
    }

    public int size() {
        return queue.size();
    }

    public int getCapacity() {
        return capacity;
    }

    public int remainingCapacity() {
        return queue.remainingCapacity();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public void clear() {
        queue.clear();
    }

    public long getEnqueuedCount() {
        return enqueuedCount.get();
    }

    public long getDequeuedCount() {
        return dequeuedCount.get();
    }

    public long getDroppedCount() {
        return droppedCount.get();
    }
}
