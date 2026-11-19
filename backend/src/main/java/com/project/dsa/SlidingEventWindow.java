package com.project.dsa;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe Sliding Window backed by a Deque.
 * Evicts elements both by maximum count N and elapsed time window T.
 *
 * @param <T> Element type
 */
public class SlidingEventWindow<T> {

    public static class TimestampedEntry<E> {
        private final E value;
        private final Instant timestamp;

        public TimestampedEntry(E value) {
            this(value, Instant.now());
        }

        public TimestampedEntry(E value, Instant timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }

        public E getValue() {
            return value;
        }

        public Instant getTimestamp() {
            return timestamp;
        }
    }

    private final Deque<TimestampedEntry<T>> deque = new ArrayDeque<>();
    private final int maxCount;
    private final Duration maxDuration;
    private final ReentrantLock lock = new ReentrantLock();

    public SlidingEventWindow(int maxCount, Duration maxDuration) {
        if (maxCount <= 0 || maxDuration == null || maxDuration.isNegative() || maxDuration.isZero()) {
            throw new IllegalArgumentException("Invalid window parameters");
        }
        this.maxCount = maxCount;
        this.maxDuration = maxDuration;
    }

    public void add(T item) {
        add(item, Instant.now());
    }

    public void add(T item, Instant timestamp) {
        if (item == null) return;

        lock.lock();
        try {
            deque.addLast(new TimestampedEntry<>(item, timestamp));
            evict();
        } finally {
            lock.unlock();
        }
    }

    private void evict() {
        Instant cutoff = Instant.now().minus(maxDuration);

        // Evict expired entries by time T
        while (!deque.isEmpty() && deque.peekFirst().getTimestamp().isBefore(cutoff)) {
            deque.pollFirst();
        }

        // Evict excess entries by count N
        while (deque.size() > maxCount) {
            deque.pollFirst();
        }
    }

    public List<T> getEntries() {
        lock.lock();
        try {
            evict();
            List<T> list = new ArrayList<>(deque.size());
            for (TimestampedEntry<T> entry : deque) {
                list.add(entry.getValue());
            }
            return list;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            evict();
            return deque.size();
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            deque.clear();
        } finally {
            lock.unlock();
        }
    }
}
