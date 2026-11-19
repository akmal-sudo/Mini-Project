package com.project.dsa;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Top-K Tracker implemented using a Min-Heap of bounded capacity K.
 * Time Complexity: O(N log K) to find Top-K elements out of N items.
 */
public class TopKTracker {

    @Getter
    @ToString
    @AllArgsConstructor
    public static class ScoredItem {
        private final String key;
        private final double score;
    }

    private final int k;
    private final PriorityQueue<ScoredItem> minHeap;
    private final ReentrantLock lock = new ReentrantLock();

    public TopKTracker(int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("K must be positive");
        }
        this.k = k;
        // Min-heap ordered by score ascending
        this.minHeap = new PriorityQueue<>(k, Comparator.comparingDouble(ScoredItem::getScore));
    }

    /**
     * Recomputes Top-K from an arbitrary distribution map (e.g., frequencies or critical scores).
     */
    public List<ScoredItem> computeTopK(Map<String, ? extends Number> itemScores) {
        if (itemScores == null || itemScores.isEmpty()) {
            return Collections.emptyList();
        }

        lock.lock();
        try {
            minHeap.clear();
            for (Map.Entry<String, ? extends Number> entry : itemScores.entrySet()) {
                double score = entry.getValue().doubleValue();
                ScoredItem item = new ScoredItem(entry.getKey(), score);

                if (minHeap.size() < k) {
                    minHeap.offer(item);
                } else if (minHeap.peek() != null && score > minHeap.peek().getScore()) {
                    minHeap.poll();
                    minHeap.offer(item);
                }
            }

            // Extract items and sort in descending order
            List<ScoredItem> topK = new ArrayList<>(minHeap);
            topK.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
            return topK;
        } finally {
            lock.unlock();
        }
    }

    public int getK() {
        return k;
    }

    public int size() {
        lock.lock();
        try {
            return minHeap.size();
        } finally {
            lock.unlock();
        }
    }
}
