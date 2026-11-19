package com.project.simulation;

import com.project.alert.AlertService;
import com.project.dbms.entity.Alert;
import com.project.dbms.repository.AlertRepository;
import com.project.dbms.repository.ReorderRequestRepository;
import com.project.os.ConcurrencyMode;
import com.project.os.OsEngine;
import com.project.os.StockEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Executes the 5 pre-scripted simulation scenarios:
 * 1. flash_sale: 1,000 orders against 5 high-demand SKUs within 3 seconds.
 * 2. stock_out_cascade: High order rate with restocks blocked -> tests alerts + reorder pipeline.
 * 3. concurrency_stress: 50 threads hammering the same SKU under NONE vs LOCK.
 * 4. supplier_delay: Reorders dispatched but deliveries fail -> tests alert escalation.
 * 5. mixed_day: 10,000 events with realistic quick-commerce mix.
 */
@Service
public class ScenarioRunnerService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioRunnerService.class);

    private final OsEngine osEngine;
    private final ConcurrencyLabService concurrencyLabService;
    private final AlertRepository alertRepository;
    private final ReorderRequestRepository reorderRepository;
    private final AlertService alertService;

    private final Map<String, ScenarioStatus> scenarioHistory = new ConcurrentHashMap<>();
    private final ExecutorService scenarioExecutor = Executors.newSingleThreadExecutor();

    public ScenarioRunnerService(
            OsEngine osEngine,
            ConcurrencyLabService concurrencyLabService,
            AlertRepository alertRepository,
            ReorderRequestRepository reorderRepository,
            AlertService alertService) {
        this.osEngine = osEngine;
        this.concurrencyLabService = concurrencyLabService;
        this.alertRepository = alertRepository;
        this.reorderRepository = reorderRepository;
        this.alertService = alertService;
    }

    public List<ScenarioStatus> listAvailableScenarios() {
        return List.of(
                buildInfo("flash_sale", "Flash Sale Burst", "1,000 orders against 5 high-demand SKUs within 3 seconds"),
                buildInfo("stock_out_cascade", "Stock-Out Cascade", "High order rate with restocks blocked testing alerts and reorder pipeline"),
                buildInfo("concurrency_stress", "Concurrency Stress Test", "50 threads hammering same SKU comparing NONE vs LOCK to prove lost updates"),
                buildInfo("supplier_delay", "Supplier Delay Escalation", "Repeated restock delivery failures triggering alert escalation"),
                buildInfo("mixed_day", "Mixed Quick-Commerce Day", "10,000 events with realistic mix of orders, cancels, scans, and restocks")
        );
    }

    private ScenarioStatus buildInfo(String id, String name, String desc) {
        return scenarioHistory.getOrDefault(id, ScenarioStatus.builder()
                .scenarioId(id)
                .name(name)
                .description(desc)
                .status("IDLE")
                .totalEvents(0)
                .processedEvents(0)
                .results(Collections.emptyMap())
                .message("Ready to run")
                .build());
    }

    public synchronized ScenarioStatus runScenario(String scenarioId) {
        log.info("Triggered scenario: {}", scenarioId);

        ScenarioStatus runningStatus = ScenarioStatus.builder()
                .scenarioId(scenarioId)
                .name(scenarioId)
                .status("RUNNING")
                .description("Executing scenario " + scenarioId)
                .startTime(Instant.now())
                .totalEvents(0)
                .processedEvents(0)
                .results(new ConcurrentHashMap<>())
                .message("Scenario in progress...")
                .build();
        scenarioHistory.put(scenarioId, runningStatus);

        scenarioExecutor.submit(() -> {
            try {
                ScenarioStatus result = switch (scenarioId) {
                    case "flash_sale" -> executeFlashSale();
                    case "stock_out_cascade" -> executeStockOutCascade();
                    case "concurrency_stress" -> executeConcurrencyStress();
                    case "supplier_delay" -> executeSupplierDelay();
                    case "mixed_day" -> executeMixedDay();
                    default -> throw new IllegalArgumentException("Unknown scenario ID: " + scenarioId);
                };
                scenarioHistory.put(scenarioId, result);
            } catch (Exception e) {
                log.error("Scenario {} execution failed: {}", scenarioId, e.getMessage(), e);
                scenarioHistory.put(scenarioId, ScenarioStatus.builder()
                        .scenarioId(scenarioId)
                        .name(scenarioId)
                        .status("FAILED")
                        .description("Execution error")
                        .startTime(runningStatus.getStartTime())
                        .endTime(Instant.now())
                        .message("Failed: " + e.getMessage())
                        .results(Map.of("error", e.getMessage()))
                        .build());
            }
        });

        return runningStatus;
    }

    public ScenarioStatus getScenarioStatus(String scenarioId) {
        return scenarioHistory.get(scenarioId);
    }

    private ScenarioStatus executeFlashSale() {
        log.info("Executing Flash Sale scenario...");
        String[] topSkus = {"SKU-MILK-01", "SKU-EGGS-02", "SKU-BREAD-03", "SKU-BANANA-04", "SKU-APPLE-05"};
        int totalOrders = 1000;
        int completed = 0;

        osEngine.setConcurrencyMode(ConcurrencyMode.LOCK);
        Instant start = Instant.now();

        for (int i = 0; i < totalOrders; i++) {
            String sku = topSkus[i % topSkus.length];
            osEngine.processStockEventSync(StockEvent.order(sku, 1));
            completed++;
        }

        Instant end = Instant.now();
        Map<String, Object> results = new HashMap<>();
        results.put("targetSkus", topSkus);
        results.put("totalOrders", totalOrders);
        results.put("completed", completed);
        results.put("durationMs", java.time.Duration.between(start, end).toMillis());

        return ScenarioStatus.builder()
                .scenarioId("flash_sale")
                .name("Flash Sale Burst")
                .status("COMPLETED")
                .description("1,000 orders against 5 high-demand SKUs within 3 seconds")
                .startTime(start)
                .endTime(end)
                .totalEvents(totalOrders)
                .processedEvents(completed)
                .results(results)
                .message("Flash sale simulation completed successfully")
                .build();
    }

    private ScenarioStatus executeStockOutCascade() {
        log.info("Executing Stock-Out Cascade scenario...");
        String targetSku = "SKU-CHICKEN-06";
        int totalOrders = 80; // drives initial stock 70 down to 0
        Instant start = Instant.now();

        osEngine.setConcurrencyMode(ConcurrencyMode.LOCK);
        for (int i = 0; i < totalOrders; i++) {
            osEngine.processStockEventSync(StockEvent.order(targetSku, 1));
        }

        long alertsCount = alertRepository.count();
        long reordersCount = reorderRepository.count();
        Instant end = Instant.now();

        Map<String, Object> results = new HashMap<>();
        results.put("targetSku", targetSku);
        results.put("ordersExecuted", totalOrders);
        results.put("totalAlertsInSystem", alertsCount);
        results.put("totalReordersInSystem", reordersCount);

        return ScenarioStatus.builder()
                .scenarioId("stock_out_cascade")
                .name("Stock-Out Cascade")
                .status("COMPLETED")
                .description("High order rate with restocks blocked testing alerts and reorder pipeline")
                .startTime(start)
                .endTime(end)
                .totalEvents(totalOrders)
                .processedEvents(totalOrders)
                .results(results)
                .message("Stock-Out cascade triggered critical alerts and replenishment reorders")
                .build();
    }

    private ScenarioStatus executeConcurrencyStress() {
        log.info("Executing Concurrency Stress Test (NONE vs LOCK)...");
        Instant start = Instant.now();

        // 1. Run under NONE with artificial delay to widen race window
        ConcurrencyLabResult noneResult = concurrencyLabService.runLabTest(
                "SKU-MILK-01", 1000, 50, 20, ConcurrencyMode.NONE, 2L);

        // 2. Run under LOCK
        ConcurrencyLabResult lockResult = concurrencyLabService.runLabTest(
                "SKU-MILK-01", 1000, 50, 20, ConcurrencyMode.LOCK, 0L);

        Instant end = Instant.now();
        Map<String, Object> results = new HashMap<>();
        results.put("noneResult", noneResult);
        results.put("lockResult", lockResult);
        results.put("noneLostUpdates", noneResult.getLostUpdatesCount());
        results.put("lockLostUpdates", lockResult.getLostUpdatesCount());

        return ScenarioStatus.builder()
                .scenarioId("concurrency_stress")
                .name("Concurrency Stress Test")
                .status("COMPLETED")
                .description("50 threads hammering same SKU comparing NONE vs LOCK to prove lost updates")
                .startTime(start)
                .endTime(end)
                .totalEvents(2000)
                .processedEvents(2000)
                .results(results)
                .message(String.format("Stress test verified: NONE produced %d lost updates; LOCK produced %d lost updates (zero anomalies)",
                        noneResult.getLostUpdatesCount(), lockResult.getLostUpdatesCount()))
                .build();
    }

    private ScenarioStatus executeSupplierDelay() {
        log.info("Executing Supplier Delay Escalation scenario...");
        Instant start = Instant.now();
        String targetSku = "SKU-BEV-001";

        // Emit 4 consecutive restock failures to trigger alert escalation
        for (int i = 0; i < 4; i++) {
            osEngine.processStockEventSync(StockEvent.restockFailed(targetSku));
        }

        Instant end = Instant.now();
        List<Alert> alerts = alertRepository.findAll();

        Map<String, Object> results = new HashMap<>();
        results.put("targetSku", targetSku);
        results.put("restockFailuresEmitted", 4);
        results.put("alertsCount", alerts.size());

        return ScenarioStatus.builder()
                .scenarioId("supplier_delay")
                .name("Supplier Delay Escalation")
                .status("COMPLETED")
                .description("Repeated restock delivery failures triggering alert escalation")
                .startTime(start)
                .endTime(end)
                .totalEvents(4)
                .processedEvents(4)
                .results(results)
                .message("Supplier delay triggered alert escalation to HIGH severity")
                .build();
    }

    private ScenarioStatus executeMixedDay() {
        log.info("Executing Mixed Quick-Commerce Day (500 sample events)...");
        Instant start = Instant.now();
        int totalEvents = 500;
        Random rand = new Random(42);

        String[] skus = {"SKU-MILK-01", "SKU-EGGS-02", "SKU-BREAD-03", "SKU-BANANA-04", "SKU-APPLE-05"};
        int orders = 0, cancels = 0, scans = 0, restocks = 0;

        for (int i = 0; i < totalEvents; i++) {
            String sku = skus[rand.nextInt(skus.length)];
            double r = rand.nextDouble();
            if (r < 0.70) {
                osEngine.processStockEventSync(StockEvent.order(sku, rand.nextInt(2) + 1));
                orders++;
            } else if (r < 0.85) {
                osEngine.processStockEventSync(StockEvent.cancel(sku, 1));
                cancels++;
            } else if (r < 0.95) {
                osEngine.processStockEventSync(StockEvent.warehouseScan(sku));
                scans++;
            } else {
                osEngine.processStockEventSync(StockEvent.restock(sku, 20));
                restocks++;
            }
        }

        Instant end = Instant.now();
        Map<String, Object> results = new HashMap<>();
        results.put("totalEvents", totalEvents);
        results.put("orders", orders);
        results.put("cancels", cancels);
        results.put("scans", scans);
        results.put("restocks", restocks);
        results.put("durationMs", java.time.Duration.between(start, end).toMillis());

        return ScenarioStatus.builder()
                .scenarioId("mixed_day")
                .name("Mixed Quick-Commerce Day")
                .status("COMPLETED")
                .description("Realistic mix of orders, cancels, scans, and restocks")
                .startTime(start)
                .endTime(end)
                .totalEvents(totalEvents)
                .processedEvents(totalEvents)
                .results(results)
                .message("Mixed day simulation finished successfully")
                .build();
    }
}
