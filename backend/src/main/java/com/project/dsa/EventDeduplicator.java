package com.project.dsa;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe HashSet deduplicator verifying uniqueness of event_id before processing.
 * Backs up database-level UNIQUE constraints to prevent duplicate execution under load.
 */
public class EventDeduplicator {

    private final Set<String> seenEventIds;
    private final AtomicLong duplicatesRejected = new AtomicLong(0);

    public EventDeduplicator() {
        this(50000);
    }

    public EventDeduplicator(int expectedCapacity) {
        this.seenEventIds = Collections.newSetFromMap(new ConcurrentHashMap<>(expectedCapacity));
    }

    /**
     * Checks and records an event_id.
     *
     * @param eventId the unique event identifier
     * @return true if event is unique and accepted; false if already seen (duplicate).
     */
    public boolean checkAndAdd(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }

        boolean isNew = seenEventIds.add(eventId);
        if (!isNew) {
            duplicatesRejected.incrementAndGet();
        }
        return isNew;
    }

    public boolean contains(String eventId) {
        return eventId != null && seenEventIds.contains(eventId);
    }

    public void remove(String eventId) {
        if (eventId != null) {
            seenEventIds.remove(eventId);
        }
    }

    public void clear() {
        seenEventIds.clear();
        duplicatesRejected.set(0);
    }

    public int size() {
        return seenEventIds.size();
    }

    public long getDuplicatesRejectedCount() {
        return duplicatesRejected.get();
    }
}
