package com.project.dsa;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Thread-safe Max-Heap scheduling replenishment requests ordered by priorityScore descending.
 */
public class ReorderPriorityHeap {

    @Getter
    @ToString
    @AllArgsConstructor
    public static class PrioritizedReorder {
        private final String sku;
        private final int vendorId;
        private final int quantity;
        private final BigDecimal priorityScore;
        private final String severity;
        private final Long alertId;
        private final Instant timestamp;
    }

    private static final Comparator<PrioritizedReorder> COMPARATOR =
            (a, b) -> b.getPriorityScore().compareTo(a.getPriorityScore());

    private final PriorityBlockingQueue<PrioritizedReorder> heap =
            new PriorityBlockingQueue<>(100, COMPARATOR);

    public void enqueue(PrioritizedReorder request) {
        if (request != null) {
            heap.offer(request);
        }
    }

    public Optional<PrioritizedReorder> dequeue() {
        return Optional.ofNullable(heap.poll());
    }

    public Optional<PrioritizedReorder> peek() {
        return Optional.ofNullable(heap.peek());
    }

    public List<PrioritizedReorder> drainAll() {
        List<PrioritizedReorder> list = new ArrayList<>();
        heap.drainTo(list);
        list.sort(COMPARATOR);
        return list;
    }

    public int size() {
        return heap.size();
    }

    public boolean isEmpty() {
        return heap.isEmpty();
    }

    public void clear() {
        heap.clear();
    }
}
