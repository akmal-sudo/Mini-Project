package com.project.simulation;

import com.project.config.EmbeddedPostgresConfig;
import com.project.dbms.service.ConsistencyCheckService;
import com.project.os.ConcurrencyMode;
import com.project.os.ExecutionResult;
import com.project.os.OsEngine;
import com.project.os.StockEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(EmbeddedPostgresConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("CONCURRENCY")
class ConcurrencyLabIntegrationTest {

    @Autowired
    private ConcurrencyLabService concurrencyLabService;

    @Autowired
    private ConsistencyCheckService consistencyCheckService;

    @Autowired
    private OsEngine osEngine;

    @Test
    @Tag("AC-01")
    @DisplayName("AC-01: 50 threads x 20 orders under LOCK results in EXACTLY 0 final stock, 1000 movements, 0 lost updates")
    void testAc01LockSerializability() {
        ConcurrencyLabResult result = concurrencyLabService.runLabTest(
                "SKU-MILK-01", 1000, 50, 20, ConcurrencyMode.LOCK, 0L);

        assertEquals(0, result.getActualFinalStock(), "Final stock must be exactly 0");
        assertEquals(0, result.getExpectedFinalStock(), "Expected stock must be 0");
        assertEquals(1000, result.getMovementsCount(), "Exactly 1,000 movements must be created");
        assertEquals(0, result.getLostUpdatesCount(), "Zero lost updates must occur under LOCK");
        assertTrue(result.isConsistent(), "Result must be marked consistent");

        // Verify ledger consistency view
        ConsistencyCheckService.ConsistencyReport audit = consistencyCheckService.runConsistencyCheck();
        assertTrue(audit.isConsistent(), "Database consistency view must report clean with zero discrepancies");
    }

    @Test
    @Tag("AC-02")
    @DisplayName("AC-02: Same test under NONE produces visible lost updates and inconsistent final stock")
    void testAc02NoneProducesLostUpdates() {
        // Run with 2ms artificial delay to widen race window
        ConcurrencyLabResult result = concurrencyLabService.runLabTest(
                "SKU-EGGS-02", 1000, 50, 20, ConcurrencyMode.NONE, 2L);

        assertTrue(result.getActualFinalStock() > 0 || result.getLostUpdatesCount() > 0,
                "NONE mode must visibly produce lost updates (final stock > 0 or lost updates > 0)");
        assertFalse(result.isConsistent(), "NONE mode result must NOT be marked consistent");
    }

    @Test
    @Tag("AC-07")
    @DisplayName("AC-07: DB_ONLY and OPTIMISTIC modes achieve serializability with 0 lost updates")
    void testDbOnlyAndOptimisticModes() {
        // Test DB_ONLY
        ConcurrencyLabResult dbOnlyResult = concurrencyLabService.runLabTest(
                "SKU-BREAD-03", 200, 20, 10, ConcurrencyMode.DB_ONLY, 0L);
        assertEquals(0, dbOnlyResult.getActualFinalStock());
        assertEquals(0, dbOnlyResult.getLostUpdatesCount());
        assertTrue(dbOnlyResult.isConsistent());

        // Test OPTIMISTIC
        ConcurrencyLabResult optResult = concurrencyLabService.runLabTest(
                "SKU-BANANA-04", 200, 20, 10, ConcurrencyMode.OPTIMISTIC, 0L);
        assertEquals(0, optResult.getActualFinalStock());
        assertEquals(0, optResult.getLostUpdatesCount());
        assertTrue(optResult.isConsistent());
    }

    @Test
    @Tag("R2")
    @DisplayName("R2: Deadlock stress test completes without permanent deadlock under lock-ordering rule")
    void testDeadlockStressTest() throws InterruptedException {
        osEngine.setConcurrencyMode(ConcurrencyMode.LOCK);
        int threadCount = 20;
        int operationsPerThread = 25;

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger failures = new AtomicInteger(0);

        String skuA = "SKU-MILK-01";
        String skuB = "SKU-EGGS-02";

        for (int i = 0; i < threadCount; i++) {
            final boolean even = (i % 2 == 0);
            pool.execute(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < operationsPerThread; j++) {
                        String firstSku = even ? skuA : skuB;
                        String secondSku = even ? skuB : skuA;

                        ExecutionResult r1 = osEngine.processStockEventSync(StockEvent.order(firstSku, 1));
                        ExecutionResult r2 = osEngine.processStockEventSync(StockEvent.order(secondSku, 1));

                        if (!r1.isSuccess() && "FAILED".equals(r1.getStatus())) failures.incrementAndGet();
                        if (!r2.isSuccess() && "FAILED".equals(r2.getStatus())) failures.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertTrue(completed, "Deadlock stress test must complete within 30s without deadlock");
        assertEquals(0, failures.get(), "Zero operational lock failures expected");
    }
}
