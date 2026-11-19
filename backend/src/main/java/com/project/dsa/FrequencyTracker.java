package com.project.dsa;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe frequency counter tracking event occurrence distributions
 * per product SKU and per event type within the sliding window.
 */
public class FrequencyTracker {

    private final Map<String, AtomicLong> skuFrequency = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> eventTypeFrequency = new ConcurrentHashMap<>();

    public void record(String sku, String eventType) {
        if (sku != null && !sku.isBlank()) {
            skuFrequency.computeIfAbsent(sku, k -> new AtomicLong(0)).incrementAndGet();
        }
        if (eventType != null && !eventType.isBlank()) {
            eventTypeFrequency.computeIfAbsent(eventType, k -> new AtomicLong(0)).incrementAndGet();
        }
    }

    public long getCountForSku(String sku) {
        if (sku == null) return 0;
        AtomicLong count = skuFrequency.get(sku);
        return count != null ? count.get() : 0;
    }

    public long getCountForEventType(String eventType) {
        if (eventType == null) return 0;
        AtomicLong count = eventTypeFrequency.get(eventType);
        return count != null ? count.get() : 0;
    }

    public Map<String, Long> getSkuFrequencies() {
        Map<String, Long> result = new HashMap<>();
        skuFrequency.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    public Map<String, Long> getEventTypeFrequencies() {
        Map<String, Long> result = new HashMap<>();
        eventTypeFrequency.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    public void clear() {
        skuFrequency.clear();
        eventTypeFrequency.clear();
    }
}
