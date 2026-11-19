package com.project.dsa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Tag("DSA")
class DsaEngineTest {

    @Test
    @Tag("AC-04")
    @Tag("NFR-09")
    @DisplayName("AC-04: KMPMatcher detects exact patterns and handles edge cases without false positives")
    void testKmpSignatureDetection() {
        KMPMatcher kmp = new KMPMatcher();

        // 1. Exact matches at start, middle, and end of message
        assertTrue(kmp.matches("STOCK_OUT occurred at shelf 4", "STOCK_OUT"));
        assertTrue(kmp.matches("Warning: current inventory level LOW_STOCK immediately", "LOW_STOCK"));
        assertTrue(kmp.matches("Transaction aborted due to ORDER_FAILED", "ORDER_FAILED"));
        assertTrue(kmp.matches("RESTOCK_FAILED", "RESTOCK_FAILED"));

        // 2. Multi-pattern scan
        Set<String> detected = kmp.scanText("Alert: STOCK_OUT and ORDER_FAILED detected simultaneously");
        assertEquals(2, detected.size());
        assertTrue(detected.contains("STOCK_OUT"));
        assertTrue(detected.contains("ORDER_FAILED"));

        // 3. Edge Cases
        assertFalse(kmp.matches("Normal transaction completed successfully", "STOCK_OUT"));
        assertFalse(kmp.matches("", "STOCK_OUT"));
        assertFalse(kmp.matches(null, "STOCK_OUT"));
        assertFalse(kmp.matches("ORDER_FAIL", "ORDER_FAILED")); // Incomplete pattern
        assertFalse(kmp.matches("any text", "UNKNOWN_PATTERN")); // Unregistered pattern

        // Pattern registration edge cases
        kmp.registerPattern(null, "TEST");
        kmp.registerPattern("TEST", null);
        kmp.registerPattern("TEST", "");
        assertTrue(kmp.scanText(null).isEmpty());
        assertTrue(kmp.scanText("").isEmpty());

        // 4. Overlapping and repetitive pattern test
        kmp.registerPattern("ABA", "ABA");
        assertTrue(kmp.matches("ABABA", "ABA"));

        // Verify comparison counters increment
        assertTrue(kmp.getScansCount() > 0);
        assertTrue(kmp.getMatchesCount() > 0);
        assertTrue(kmp.getComparisonsCount() > 0);
        assertNotNull(kmp.getRegisteredPatterns());
    }

    @Test
    @Tag("AC-04")
    @DisplayName("R4: Precision and Recall evaluation on labelled test log set (target precision >= 0.95)")
    void testKmpPrecisionAndRecallOnLabelledLogs() {
        KMPMatcher kmp = new KMPMatcher();

        // Labelled evaluation dataset: (Log Line, Expected Detected Pattern or null)
        Map<String, String> testDataset = Map.ofEntries(
                Map.entry("2026-09-29T10:15:30.123Z | WARN | SKU=85123A | EVENT=ORDER | MSG=LOW_STOCK qty=4 threshold=10", "LOW_STOCK"),
                Map.entry("2026-09-29T10:15:31.456Z | ERROR | SKU=22423 | EVENT=ORDER | MSG=STOCK_OUT item unavailable", "STOCK_OUT"),
                Map.entry("2026-09-29T10:15:32.789Z | ERROR | SKU=71053 | EVENT=ORDER | MSG=ORDER_FAILED database timeout", "ORDER_FAILED"),
                Map.entry("2026-09-29T10:15:33.001Z | WARN | SKU=84406B | EVENT=RESTOCK | MSG=RESTOCK_FAILED vendor rejected", "RESTOCK_FAILED"),
                Map.entry("2026-09-29T10:15:34.111Z | INFO | SKU=84029G | EVENT=ORDER | MSG=Order completed qty=2", "NONE"),
                Map.entry("2026-09-29T10:15:35.222Z | INFO | SKU=85123A | EVENT=WAREHOUSE_SCAN | MSG=Bin verified ok", "NONE"),
                Map.entry("2026-09-29T10:15:36.333Z | INFO | SKU=22720 | EVENT=CANCEL | MSG=Customer canceled order", "NONE"),
                Map.entry("2026-09-29T10:15:37.444Z | WARN | SKU=21733 | EVENT=ORDER | MSG=LOW_STOCK remaining=2", "LOW_STOCK"),
                Map.entry("2026-09-29T10:15:38.555Z | ERROR | SKU=82482 | EVENT=ORDER | MSG=STOCK_OUT zero remaining", "STOCK_OUT"),
                Map.entry("2026-09-29T10:15:39.666Z | INFO | SKU=82486 | EVENT=ORDER | MSG=Payment processed successfully", "NONE")
        );

        int truePositives = 0;
        int falsePositives = 0;
        int falseNegatives = 0;

        for (Map.Entry<String, String> sample : testDataset.entrySet()) {
            Set<String> matches = kmp.scanText(sample.getKey());
            String expected = sample.getValue();

            if (!expected.equals("NONE")) {
                if (matches.contains(expected)) {
                    truePositives++;
                } else {
                    falseNegatives++;
                }
            } else {
                if (!matches.isEmpty()) {
                    falsePositives++;
                }
            }
        }

        double precision = (double) truePositives / (truePositives + falsePositives);
        double recall = (double) truePositives / (truePositives + falseNegatives);

        assertTrue(precision >= 0.95, "Precision must be >= 0.95, was: " + precision);
        assertTrue(recall >= 0.95, "Recall must be >= 0.95, was: " + recall);
    }

    @Test
    @Tag("FR-08")
    @DisplayName("EventDeduplicator: detects duplicates and tracks rejection metrics")
    void testEventDeduplicator() {
        EventDeduplicator dedup = new EventDeduplicator();

        assertTrue(dedup.checkAndAdd("EVT-1001"));
        assertTrue(dedup.checkAndAdd("EVT-1002"));
        assertFalse(dedup.checkAndAdd("EVT-1001"), "Duplicate EVT-1001 must be rejected");
        assertFalse(dedup.checkAndAdd("EVT-1001"), "Duplicate EVT-1001 must be rejected again");
        assertFalse(dedup.checkAndAdd(null));
        assertFalse(dedup.checkAndAdd(""));

        assertEquals(2, dedup.size());
        assertEquals(2, dedup.getDuplicatesRejectedCount());
        assertTrue(dedup.contains("EVT-1001"));
        assertFalse(dedup.contains("EVT-9999"));
        assertFalse(dedup.contains(null));

        dedup.remove("EVT-1001");
        dedup.remove(null);
        assertFalse(dedup.contains("EVT-1001"));

        dedup.clear();
        assertEquals(0, dedup.size());
    }

    @Test
    @Tag("FR-08")
    @Tag("FR-14")
    @DisplayName("EventFifoQueue: preserves FIFO order, timeout polling, and handles capacity")
    void testEventFifoQueue() throws InterruptedException {
        assertThrows(IllegalArgumentException.class, () -> new EventFifoQueue<>(0));

        EventFifoQueue<String> queue = new EventFifoQueue<>(2);
        assertFalse(queue.offer(null));

        assertTrue(queue.offer("MSG-1", 10, TimeUnit.MILLISECONDS));
        assertTrue(queue.offer("MSG-2"));
        assertFalse(queue.offer("MSG-3", 10, TimeUnit.MILLISECONDS), "Queue capacity is 2; 3rd message must be rejected");

        assertEquals(2, queue.size());
        assertEquals(2, queue.getCapacity());
        assertEquals(0, queue.remainingCapacity());
        assertEquals(1, queue.getDroppedCount());
        assertEquals(2, queue.getEnqueuedCount());

        assertEquals(Optional.of("MSG-1"), queue.poll(10, TimeUnit.MILLISECONDS));
        assertEquals(Optional.of("MSG-2"), queue.poll());
        assertTrue(queue.isEmpty());
        assertEquals(Optional.empty(), queue.poll());
        assertEquals(2, queue.getDequeuedCount());

        queue.offer("MSG-CLEARED");
        queue.clear();
        assertTrue(queue.isEmpty());
    }

    @Test
    @DisplayName("SlidingEventWindow: evicts by count N and duration T")
    void testSlidingEventWindow() throws InterruptedException {
        assertThrows(IllegalArgumentException.class, () -> new SlidingEventWindow<>(0, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new SlidingEventWindow<>(10, null));

        SlidingEventWindow<String> window = new SlidingEventWindow<>(3, Duration.ofMillis(100));

        window.add(null);
        window.add("A");
        window.add("B");
        window.add("C");
        window.add("D"); // Evicts "A" because maxCount=3

        List<String> entries = window.getEntries();
        assertEquals(3, entries.size());
        assertEquals(List.of("B", "C", "D"), entries);

        // Sleep to test time eviction
        Thread.sleep(120);
        assertEquals(0, window.size(), "All items should expire after duration T");

        window.add("X");
        window.clear();
        assertEquals(0, window.size());
    }

    @Test
    @DisplayName("FrequencyTracker: maintains per-SKU and per-event-type counts")
    void testFrequencyTracker() {
        FrequencyTracker tracker = new FrequencyTracker();

        tracker.record(null, null);
        tracker.record("SKU-1", "ORDER");
        tracker.record("SKU-1", "ORDER");
        tracker.record("SKU-1", "RESTOCK");
        tracker.record("SKU-2", "ORDER");

        assertEquals(3, tracker.getCountForSku("SKU-1"));
        assertEquals(1, tracker.getCountForSku("SKU-2"));
        assertEquals(0, tracker.getCountForSku("SKU-NONEXISTENT"));
        assertEquals(0, tracker.getCountForSku(null));

        assertEquals(3, tracker.getCountForEventType("ORDER"));
        assertEquals(1, tracker.getCountForEventType("RESTOCK"));
        assertEquals(0, tracker.getCountForEventType(null));

        assertEquals(2, tracker.getSkuFrequencies().size());
        assertEquals(2, tracker.getEventTypeFrequencies().size());

        tracker.clear();
        assertEquals(0, tracker.getCountForSku("SKU-1"));
    }

    @Test
    @DisplayName("ReorderPriorityHeap: orders requests by priorityScore descending")
    void testReorderPriorityHeap() {
        ReorderPriorityHeap heap = new ReorderPriorityHeap();

        ReorderPriorityHeap.PrioritizedReorder low = new ReorderPriorityHeap.PrioritizedReorder(
                "SKU-LOW", 1, 10, new BigDecimal("120.00"), "LOW", 1L, Instant.now());
        ReorderPriorityHeap.PrioritizedReorder high = new ReorderPriorityHeap.PrioritizedReorder(
                "SKU-HIGH", 1, 20, new BigDecimal("350.00"), "HIGH", 2L, Instant.now());
        ReorderPriorityHeap.PrioritizedReorder crit = new ReorderPriorityHeap.PrioritizedReorder(
                "SKU-CRIT", 2, 50, new BigDecimal("450.00"), "CRITICAL", 3L, Instant.now());

        heap.enqueue(null);
        heap.enqueue(low);
        heap.enqueue(crit);
        heap.enqueue(high);

        assertEquals(3, heap.size());
        assertFalse(heap.isEmpty());
        assertTrue(heap.peek().isPresent());
        assertEquals("SKU-CRIT", heap.peek().get().getSku());

        assertEquals("SKU-CRIT", heap.dequeue().orElseThrow().getSku());
        assertEquals("SKU-HIGH", heap.dequeue().orElseThrow().getSku());
        assertEquals("SKU-LOW", heap.dequeue().orElseThrow().getSku());
        assertTrue(heap.dequeue().isEmpty());

        heap.enqueue(low);
        heap.enqueue(high);
        List<ReorderPriorityHeap.PrioritizedReorder> drained = heap.drainAll();
        assertEquals(2, drained.size());
        assertEquals("SKU-HIGH", drained.get(0).getSku());

        heap.enqueue(low);
        heap.clear();
        assertTrue(heap.isEmpty());
    }

    @Test
    @DisplayName("TopKTracker: min-heap maintains top K highest scoring items")
    void testTopKTracker() {
        assertThrows(IllegalArgumentException.class, () -> new TopKTracker(0));

        TopKTracker topK = new TopKTracker(3);
        assertEquals(3, topK.getK());
        assertTrue(topK.computeTopK(null).isEmpty());
        assertTrue(topK.computeTopK(Collections.emptyMap()).isEmpty());

        Map<String, Integer> counts = Map.of(
                "SKU-A", 10,
                "SKU-B", 50,
                "SKU-C", 5,
                "SKU-D", 100,
                "SKU-E", 75
        );

        List<TopKTracker.ScoredItem> result = topK.computeTopK(counts);

        assertEquals(3, result.size());
        assertEquals("SKU-D", result.get(0).getKey());
        assertEquals(100.0, result.get(0).getScore());
        assertEquals("SKU-E", result.get(1).getKey());
        assertEquals(75.0, result.get(1).getScore());
        assertEquals("SKU-B", result.get(2).getKey());
        assertEquals(50.0, result.get(2).getScore());
        assertEquals(3, topK.size());
    }

    @Test
    @DisplayName("InventoryCache: write-through caching operations")
    void testInventoryCache() {
        InventoryCache cache = new InventoryCache();

        cache.put(null, 10, 5, 20, 1L);
        cache.put("SKU-X", 25, 10, 50, 1L);
        assertTrue(cache.get("SKU-X").isPresent());
        assertEquals(25, cache.get("SKU-X").get().getQuantity());
        assertEquals(10, cache.get("SKU-X").get().getReorderThreshold());
        assertEquals(50, cache.get("SKU-X").get().getReorderQuantity());
        assertEquals(1L, cache.get("SKU-X").get().getVersion());
        assertNotNull(cache.get("SKU-X").get().getUpdatedAt());
        assertFalse(cache.get(null).isPresent());
        assertEquals(1, cache.size());
        assertEquals(1, cache.getAllSnapshots().size());

        cache.remove("SKU-X");
        cache.remove(null);
        assertFalse(cache.get("SKU-X").isPresent());

        cache.put("SKU-Y", 10, 2, 5, 1L);
        cache.clear();
        assertEquals(0, cache.size());
    }

    @Test
    @DisplayName("DsaEngine: generates complete metrics snapshot")
    void testDsaEngineMetricsSnapshot() {
        DsaEngine dsa = new DsaEngineImpl(100, 50, 30, 3);
        assertNotNull(dsa.getKmpMatcher());
        assertNotNull(dsa.getInventoryCache());
        assertNotNull(dsa.getDeduplicator());
        assertNotNull(dsa.getOrderQueue());
        assertNotNull(dsa.getLogQueue());
        assertNotNull(dsa.getSlidingWindow());
        assertNotNull(dsa.getFrequencyTracker());
        assertNotNull(dsa.getPriorityHeap());
        assertNotNull(dsa.getTopKTracker());

        DsaMetricsSnapshot snapshot = dsa.getMetricsSnapshot();
        assertNotNull(snapshot);
        assertEquals(0, snapshot.getOrderQueueSize());
        assertEquals(0, snapshot.getLogQueueSize());
        assertEquals(0, snapshot.getCacheSize());
        assertEquals(0, snapshot.getDeduplicatorSetSize());
        assertEquals(0, snapshot.getDuplicatesRejectedCount());
        assertEquals(0, snapshot.getSlidingWindowCount());
        assertEquals(0, snapshot.getPriorityHeapSize());
        assertEquals(0, snapshot.getTopKHeapSize());
        assertTrue(snapshot.getKmpScansCount() >= 0);
        assertTrue(snapshot.getKmpMatchesCount() >= 0);
        assertTrue(snapshot.getKmpComparisonsCount() >= 0);
    }
}
