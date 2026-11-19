package com.project.os;

/**
 * Concurrency strategies for managing atomic inventory critical sections.
 */
public enum ConcurrencyMode {
    /**
     * Naive read-modify-write without row locks or application locks.
     * Demonstrates race conditions, lost updates, and overselling.
     */
    NONE,

    /**
     * Per-SKU application-level ReentrantLock + DB SELECT ... FOR UPDATE.
     * Default production-grade mode guaranteeing strict serializability.
     */
    LOCK,

    /**
     * Database row-level locking (SELECT ... FOR UPDATE) only.
     */
    DB_ONLY,

    /**
     * Optimistic locking with JPA/explicit version column and bounded retries.
     */
    OPTIMISTIC
}
