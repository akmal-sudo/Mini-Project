package com.project.dsa;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe write-through cache for instant SKU stock and threshold lookups.
 * Database transactions remain the authoritative source of truth.
 */
public class InventoryCache {

    @Getter
    @ToString
    @AllArgsConstructor
    public static class InventorySnapshot {
        private final String sku;
        private final int quantity;
        private final int reorderThreshold;
        private final int reorderQuantity;
        private final long version;
        private final Instant updatedAt;
    }

    private final Map<String, InventorySnapshot> cache = new ConcurrentHashMap<>();

    public void put(String sku, int quantity, int threshold, int reorderQty, long version) {
        if (sku == null) return;
        cache.put(sku, new InventorySnapshot(sku, quantity, threshold, reorderQty, version, Instant.now()));
    }

    public Optional<InventorySnapshot> get(String sku) {
        if (sku == null) return Optional.empty();
        return Optional.ofNullable(cache.get(sku));
    }

    public void remove(String sku) {
        if (sku != null) cache.remove(sku);
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

    public Map<String, InventorySnapshot> getAllSnapshots() {
        return new ConcurrentHashMap<>(cache);
    }
}
