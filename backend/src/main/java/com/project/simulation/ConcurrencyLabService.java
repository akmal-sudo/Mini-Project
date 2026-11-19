package com.project.simulation;

import com.project.dbms.entity.Inventory;
import com.project.dbms.repository.InventoryMovementRepository;
import com.project.dbms.repository.InventoryRepository;
import com.project.dsa.DsaEngine;
import com.project.os.ConcurrencyMode;
import com.project.os.ExecutionResult;
import com.project.os.OsEngine;
import com.project.os.OsMetricsSnapshot;
import com.project.os.StockEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executes controlled multi-threaded bursts against a single SKU to measure
 * lost updates, race conditions, lock contention, and execution times
 * across NONE, LOCK, DB_ONLY, and OPTIMISTIC concurrency modes.
 */
@Service
public class ConcurrencyLabService {

    private static final Logger log = LoggerFactory.getLogger(ConcurrencyLabService.class);

    private final OsEngine osEngine;
    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final DsaEngine dsaEngine;
    private final JdbcTemplate jdbcTemplate;

    public ConcurrencyLabService(
            OsEngine osEngine,
            InventoryRepository inventoryRepository,
            InventoryMovementRepository movementRepository,
            DsaEngine dsaEngine,
            JdbcTemplate jdbcTemplate) {
        this.osEngine = osEngine;
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.dsaEngine = dsaEngine;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Runs a multi-threaded burst test (e.g. 50 threads x 20 orders = 1000 orders).
     */
    public ConcurrencyLabResult runLabTest(
            String sku,
            int initialStock,
            int threadCount,
            int ordersPerThread,
            ConcurrencyMode mode,
            long artificialDelayMs) {

        log.info("Starting Concurrency Lab: SKU={}, initialStock={}, threads={}, ordersPerThread={}, mode={}, delay={}ms",
                sku, initialStock, threadCount, ordersPerThread, mode, artificialDelayMs);

        // 1. Reset Inventory stock, initial_quantity & version, and clear previous movements for this SKU
        jdbcTemplate.update("DELETE FROM inventory_movements WHERE sku = ?", sku);
        jdbcTemplate.update("UPDATE inventory SET quantity = ?, initial_quantity = ?, version = 0, updated_at = now() WHERE sku = ?",
                initialStock, initialStock, sku);

        Inventory inv = inventoryRepository.findById(sku).orElse(null);
        if (inv != null) {
            dsaEngine.getInventoryCache().put(sku, initialStock, inv.getReorderThreshold(), inv.getReorderQuantity(), 0L);
        }

        // Base movements count is 0 after clearing
        long baseMovements = 0L;

        // Reset OS and lock metrics
        osEngine.resetMetrics();
        osEngine.setConcurrencyMode(mode);
        osEngine.setArtificialDelayMs(artificialDelayMs);

        int totalOrders = threadCount * ordersPerThread;
        int expectedFinalStock = Math.max(0, initialStock - totalOrders);

        ExecutorService threadPool = Executors.newFixedThreadPool(threadCount, new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "lab-worker-" + count.getAndIncrement());
            }
        });

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        Map<String, Integer> perThreadSuccess = new ConcurrentHashMap<>();
        Map<String, Integer> perThreadFailed = new ConcurrentHashMap<>();

        long startTime = System.currentTimeMillis();

        for (int t = 0; t < threadCount; t++) {
            final int threadIdx = t + 1;
            threadPool.execute(() -> {
                String threadName = "lab-worker-" + threadIdx;
                int success = 0;
                int failed = 0;
                try {
                    startLatch.await(); // Simultaneous burst start
                    for (int o = 0; o < ordersPerThread; o++) {
                        StockEvent event = StockEvent.order(sku, 1);
                        ExecutionResult result = osEngine.processStockEventSync(event);
                        if (result.isSuccess() && "COMPLETED".equals(result.getStatus())) {
                            success++;
                        } else {
                            failed++;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    perThreadSuccess.put(threadName, success);
                    perThreadFailed.put(threadName, failed);
                    doneLatch.countDown();
                }
            });
        }

        // Release the burst!
        startLatch.countDown();

        try {
            boolean completed = doneLatch.await(60, TimeUnit.SECONDS);
            if (!completed) {
                log.warn("Concurrency Lab test timed out after 60 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long elapsedTimeMs = System.currentTimeMillis() - startTime;
        threadPool.shutdown();

        // 2. Query actual results
        Integer actualFinalStock = jdbcTemplate.queryForObject(
                "SELECT quantity FROM inventory WHERE sku = ?", Integer.class, sku);
        if (actualFinalStock == null) actualFinalStock = 0;

        Long finalMovements = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM inventory_movements WHERE sku = ?", Long.class, sku);
        if (finalMovements == null) finalMovements = 0L;
        long movementsCreated = finalMovements - baseMovements;

        // Calculate lost updates
        long lostUpdates;
        if (mode == ConcurrencyMode.NONE) {
            // Under NONE, if 1000 orders were processed but final stock is e.g. 842 instead of 0,
            // 842 updates were lost!
            lostUpdates = Math.max(0, actualFinalStock - expectedFinalStock);
            if (movementsCreated > (initialStock - actualFinalStock)) {
                lostUpdates = Math.max(lostUpdates, movementsCreated - (initialStock - actualFinalStock));
            }
        } else {
            lostUpdates = Math.max(0, actualFinalStock - expectedFinalStock);
        }

        OsMetricsSnapshot metrics = osEngine.getMetricsSnapshot();
        boolean consistent = (actualFinalStock == expectedFinalStock) && (lostUpdates == 0);

        log.info("Lab complete: mode={}, expected={}, actual={}, movements={}, lostUpdates={}, elapsed={}ms",
                mode, expectedFinalStock, actualFinalStock, movementsCreated, lostUpdates, elapsedTimeMs);

        return ConcurrencyLabResult.builder()
                .sku(sku)
                .initialStock(initialStock)
                .threadCount(threadCount)
                .ordersPerThread(ordersPerThread)
                .totalOrders(totalOrders)
                .concurrencyMode(mode)
                .artificialDelayMs(artificialDelayMs)
                .expectedFinalStock(expectedFinalStock)
                .actualFinalStock(actualFinalStock)
                .movementsCount(movementsCreated)
                .lostUpdatesCount(lostUpdates)
                .elapsedTimeMs(elapsedTimeMs)
                .lockContentionCount(metrics.getLockContentionCount())
                .avgLockWaitTimeMs(metrics.getAvgLockWaitTimeMs())
                .p95LockWaitTimeMs(metrics.getP95LockWaitTimeMs())
                .perThreadSuccess(perThreadSuccess)
                .perThreadFailed(perThreadFailed)
                .consistent(consistent)
                .build();
    }
}
