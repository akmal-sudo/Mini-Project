package com.project.os;

import com.project.dbms.entity.FailedEvent;
import com.project.dbms.repository.FailedEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("OS")
class OsEngineTest {

    private InventorySyncExecutor syncExecutor;
    private SkuLockManager lockManager;
    private FailedEventRepository failedEventRepository;
    private OsEngineImpl osEngine;

    @BeforeEach
    void setUp() {
        syncExecutor = mock(InventorySyncExecutor.class);
        lockManager = new SkuLockManager();
        failedEventRepository = mock(FailedEventRepository.class);

        // Small queue capacity (5) and short backpressure timeout (50ms) for deterministic unit testing
        osEngine = new OsEngineImpl(
                5, 4, 50, "LOCK", 0,
                syncExecutor, lockManager, failedEventRepository
        );
        osEngine.init();
    }

    @AfterEach
    void tearDown() {
        osEngine.shutdown();
    }

    @Test
    @DisplayName("SkuLockManager tracks contention, acquisition count, and wait times")
    void testSkuLockManagerContention() throws InterruptedException {
        String sku = "SKU-LOCK-TEST";
        assertTrue(lockManager.tryLock(sku, 100, TimeUnit.MILLISECONDS));

        CountDownLatch threadBlocked = new CountDownLatch(1);
        CountDownLatch finishTest = new CountDownLatch(1);
        AtomicBoolean secondAcquired = new AtomicBoolean(false);

        Thread t = new Thread(() -> {
            try {
                threadBlocked.countDown();
                // Attempt to acquire while main thread holds lock -> contention incremented
                boolean acq = lockManager.tryLock(sku, 50, TimeUnit.MILLISECONDS);
                secondAcquired.set(acq);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                finishTest.countDown();
            }
        });

        t.start();
        threadBlocked.await();
        finishTest.await(2, TimeUnit.SECONDS);

        assertFalse(secondAcquired.get(), "Second thread should fail to acquire lock within 50ms");
        assertTrue(lockManager.getContentionCount() >= 1, "Contention count should be recorded");
        assertTrue(lockManager.getTotalAcquisitions() >= 1);
        assertTrue(lockManager.getAvgLockWaitTimeMs() >= 0.0);

        lockManager.unlock(sku);
    }

    @Test
    @DisplayName("Backpressure policy: Full queue rejects with timeout and writes to failed_events")
    void testBackpressureQueueOverflow() throws InterruptedException {
        // Mock syncExecutor to simulate slow processing
        when(syncExecutor.execute(any(), any(), anyLong())).thenAnswer(inv -> {
            Thread.sleep(500); // artificially hold worker
            return ExecutionResult.builder()
                    .success(true)
                    .eventId("EVT")
                    .sku("SKU-1")
                    .status("COMPLETED")
                    .build();
        });

        // Submit events rapidly to exceed queue capacity (5) + active workers (4)
        boolean hasRejection = false;
        for (int i = 0; i < 20; i++) {
            StockEvent event = StockEvent.order("SKU-OVERFLOW", 1);
            boolean accepted = osEngine.submitEvent(event);
            if (!accepted) {
                hasRejection = true;
                break;
            }
        }

        assertTrue(hasRejection, "Expected backpressure rejection when bounded queue exceeds capacity");
        OsMetricsSnapshot metrics = osEngine.getMetricsSnapshot();
        assertTrue(metrics.getRejectedOverflowCount() > 0, "Rejected overflow counter should increment");
        verify(failedEventRepository, atLeastOnce()).save(any(FailedEvent.class));
    }

    @Test
    @DisplayName("Worker pool size can be scaled up and down dynamically at runtime")
    void testDynamicWorkerPoolResize() {
        assertEquals(4, osEngine.getWorkerPoolSize());

        // Scale up to 8
        osEngine.setWorkerPoolSize(8);
        assertEquals(8, osEngine.getWorkerPoolSize());

        // Scale down to 2
        osEngine.setWorkerPoolSize(2);
        assertEquals(2, osEngine.getWorkerPoolSize());

        // Invalid bounds
        assertThrows(IllegalArgumentException.class, () -> osEngine.setWorkerPoolSize(0));
        assertThrows(IllegalArgumentException.class, () -> osEngine.setWorkerPoolSize(100));
    }

    @Test
    @DisplayName("Synchronous stock event processing records metrics and per-thread stats")
    void testSynchronousStockEventProcessing() {
        when(syncExecutor.execute(any(), any(), anyLong())).thenReturn(
                ExecutionResult.builder()
                        .success(true)
                        .eventId("EVT-1")
                        .sku("SKU-SYNC")
                        .status("COMPLETED")
                        .build()
        );

        ExecutionResult result = osEngine.processStockEventSync(StockEvent.order("SKU-SYNC", 2));
        assertTrue(result.isSuccess());

        OsMetricsSnapshot snapshot = osEngine.getMetricsSnapshot();
        assertEquals(1, snapshot.getProducedCount());
        assertEquals(1, snapshot.getProcessedCount());
        assertEquals(0, snapshot.getFailedCount());
        assertTrue(snapshot.getPerThreadProcessedCounts().containsKey(Thread.currentThread().getName()));
    }
}
