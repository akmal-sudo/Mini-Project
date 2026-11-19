package com.project.os;

import com.project.alert.AlertService;
import com.project.dbms.entity.EventLog;
import com.project.dbms.entity.FailedEvent;
import com.project.dbms.entity.Inventory;
import com.project.dbms.entity.InventoryMovement;
import com.project.dbms.entity.Order;
import com.project.dbms.repository.EventLogRepository;
import com.project.dbms.repository.FailedEventRepository;
import com.project.dbms.repository.InventoryMovementRepository;
import com.project.dbms.repository.InventoryRepository;
import com.project.dbms.repository.OrderRepository;
import com.project.dsa.DsaEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Executes the inventory critical section (Check Stock -> Deduct -> Update)
 * under one of four concurrency modes: NONE, LOCK, DB_ONLY, OPTIMISTIC.
 */
@Service
public class InventorySyncExecutor {

    private static final Logger log = LoggerFactory.getLogger(InventorySyncExecutor.class);

    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final OrderRepository orderRepository;
    private final EventLogRepository eventLogRepository;
    private final FailedEventRepository failedEventRepository;
    private final SkuLockManager skuLockManager;
    private final DsaEngine dsaEngine;
    private final AlertService alertService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public InventorySyncExecutor(
            InventoryRepository inventoryRepository,
            InventoryMovementRepository movementRepository,
            OrderRepository orderRepository,
            EventLogRepository eventLogRepository,
            FailedEventRepository failedEventRepository,
            SkuLockManager skuLockManager,
            DsaEngine dsaEngine,
            AlertService alertService,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.orderRepository = orderRepository;
        this.eventLogRepository = eventLogRepository;
        this.failedEventRepository = failedEventRepository;
        this.skuLockManager = skuLockManager;
        this.dsaEngine = dsaEngine;
        this.alertService = alertService;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public ExecutionResult execute(StockEvent event, ConcurrencyMode mode, long artificialDelayMs) {
        String type = event.getEventType();
        if ("WAREHOUSE_SCAN".equals(type)) {
            return processScan(event);
        } else if ("RESTOCK_FAILED".equals(type)) {
            return processRestockFailed(event);
        }

        return switch (mode) {
            case NONE -> executeNone(event, artificialDelayMs);
            case LOCK -> executeLock(event);
            case DB_ONLY -> executeDbOnly(event);
            case OPTIMISTIC -> executeOptimistic(event);
        };
    }

    /**
     * NONE mode: Naive read-modify-write without row locks or application locks.
     * With artificialDelayMs, widens the race window to visibly produce lost updates.
     */
    private ExecutionResult executeNone(StockEvent event, long artificialDelayMs) {
        String sku = event.getSku();
        int requestedQty = event.getQuantity();
        String eventId = event.getEventId();
        String eventType = event.getEventType();

        try {
            // 1. Naive read current stock
            Integer currentQty = jdbcTemplate.queryForObject(
                    "SELECT quantity FROM inventory WHERE sku = ?", Integer.class, sku);
            if (currentQty == null) {
                return failEvent(eventId, sku, eventType, "SKU not found in inventory: " + sku);
            }

            int delta = "ORDER".equals(eventType) ? -requestedQty : requestedQty;
            int newQty = currentQty + delta;

            if ("ORDER".equals(eventType) && currentQty < requestedQty) {
                // Insufficient stock
                recordOrder(eventId, sku, requestedQty, "REJECTED", "Insufficient stock: available " + currentQty + ", requested " + requestedQty);
                recordLog(eventId, eventType, sku, "WARN", "Order rejected for SKU " + sku + ": insufficient stock");
                return ExecutionResult.builder()
                        .success(false)
                        .eventId(eventId)
                        .sku(sku)
                        .eventType(eventType)
                        .previousQuantity(currentQty)
                        .newQuantity(currentQty)
                        .status("REJECTED")
                        .message("Insufficient stock")
                        .build();
            }

            // 2. Artificial delay to widen race condition window
            if (artificialDelayMs > 0) {
                try {
                    Thread.sleep(artificialDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // 3. Write absolute value back without checking if stock was modified concurrently!
            jdbcTemplate.update("UPDATE inventory SET quantity = ?, updated_at = now() WHERE sku = ?", newQty, sku);

            // 4. Record ledger and order
            jdbcTemplate.update(
                    "INSERT INTO inventory_movements (sku, delta, quantity_after, reason, event_id, created_at) VALUES (?, ?, ?, ?, ?, now())",
                    sku, delta, newQty, "ORDER".equals(eventType) ? "ORDER" : "RESTOCK", eventId);

            recordOrder(eventId, sku, requestedQty, "COMPLETED", null);
            recordLog(eventId, eventType, sku, "INFO", "Stock updated under NONE mode for SKU " + sku + " to " + newQty);

            // 5. Update DSA and Alert
            updateDsaAndAlert(sku, newQty, eventId, eventType);

            return ExecutionResult.builder()
                    .success(true)
                    .eventId(eventId)
                    .sku(sku)
                    .eventType(eventType)
                    .previousQuantity(currentQty)
                    .newQuantity(newQty)
                    .status("COMPLETED")
                    .message("Processed under NONE")
                    .build();

        } catch (Exception e) {
            log.warn("NONE execution exception for event {}: {}", eventId, e.getMessage());
            recordOrder(eventId, sku, requestedQty, "FAILED", e.getMessage());
            recordFailedEvent(eventId, e.getMessage());
            return failEvent(eventId, sku, eventType, e.getMessage());
        }
    }

    /**
     * LOCK mode: Per-SKU ReentrantLock + DB SELECT ... FOR UPDATE transaction.
     */
    private ExecutionResult executeLock(StockEvent event) {
        String sku = event.getSku();
        boolean acquired = false;
        try {
            // Lock ordering: 1. SKU lock first
            acquired = skuLockManager.tryLock(sku, 5, TimeUnit.SECONDS);
            if (!acquired) {
                return failEvent(event.getEventId(), sku, event.getEventType(), "Lock acquisition timeout for SKU: " + sku);
            }

            // Lock ordering: 2. DB row lock second (inside transaction)
            return transactionTemplate.execute(status -> executeInTransaction(event));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failEvent(event.getEventId(), sku, event.getEventType(), "Interrupted during lock wait");
        } finally {
            if (acquired) {
                skuLockManager.unlock(sku);
            }
        }
    }

    /**
     * DB_ONLY mode: SELECT ... FOR UPDATE row-level lock only.
     */
    private ExecutionResult executeDbOnly(StockEvent event) {
        return transactionTemplate.execute(status -> executeInTransaction(event));
    }

    /**
     * Helper to execute within a database transaction holding SELECT ... FOR UPDATE.
     */
    private ExecutionResult executeInTransaction(StockEvent event) {
        String sku = event.getSku();
        int requestedQty = event.getQuantity();
        String eventId = event.getEventId();
        String eventType = event.getEventType();

        Inventory inventory = inventoryRepository.findByIdWithPessimisticLock(sku).orElse(null);
        if (inventory == null) {
            return failEvent(eventId, sku, eventType, "SKU not found in inventory: " + sku);
        }

        int currentQty = inventory.getQuantity();
        int delta = "ORDER".equals(eventType) ? -requestedQty : requestedQty;

        if ("ORDER".equals(eventType) && currentQty < requestedQty) {
            recordOrder(eventId, sku, requestedQty, "REJECTED", "Insufficient stock: available " + currentQty + ", requested " + requestedQty);
            recordLog(eventId, eventType, sku, "WARN", "Order rejected for SKU " + sku + ": insufficient stock (" + currentQty + ")");
            return ExecutionResult.builder()
                    .success(false)
                    .eventId(eventId)
                    .sku(sku)
                    .eventType(eventType)
                    .previousQuantity(currentQty)
                    .newQuantity(currentQty)
                    .status("REJECTED")
                    .message("Insufficient stock")
                    .build();
        }

        int newQty = currentQty + delta;
        inventory.setQuantity(newQty);
        inventoryRepository.saveAndFlush(inventory);

        movementRepository.save(InventoryMovement.builder()
                .sku(sku)
                .delta(delta)
                .quantityAfter(newQty)
                .reason("ORDER".equals(eventType) ? "ORDER" : "RESTOCK")
                .eventId(eventId)
                .createdAt(Instant.now())
                .build());

        recordOrder(eventId, sku, requestedQty, "COMPLETED", null);
        recordLog(eventId, eventType, sku, "INFO", "Stock updated to " + newQty + " for " + sku);

        updateDsaAndAlert(sku, newQty, eventId, eventType);

        return ExecutionResult.builder()
                .success(true)
                .eventId(eventId)
                .sku(sku)
                .eventType(eventType)
                .previousQuantity(currentQty)
                .newQuantity(newQty)
                .status("COMPLETED")
                .message("Success")
                .build();
    }

    /**
     * OPTIMISTIC mode: Explicit version column checking with bounded retries and exponential jitter backoff.
     */
    private ExecutionResult executeOptimistic(StockEvent event) {
        String sku = event.getSku();
        int requestedQty = event.getQuantity();
        String eventId = event.getEventId();
        String eventType = event.getEventType();
        int maxRetries = 50;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            Inventory inv = inventoryRepository.findById(sku).orElse(null);
            if (inv == null) {
                return failEvent(eventId, sku, eventType, "SKU not found: " + sku);
            }

            int currentQty = inv.getQuantity();
            long currentVersion = inv.getVersion();
            int delta = "ORDER".equals(eventType) ? -requestedQty : requestedQty;

            if ("ORDER".equals(eventType) && currentQty < requestedQty) {
                recordOrder(eventId, sku, requestedQty, "REJECTED", "Insufficient stock");
                recordLog(eventId, eventType, sku, "WARN", "Insufficient stock for " + sku);
                return ExecutionResult.builder()
                        .success(false)
                        .eventId(eventId)
                        .sku(sku)
                        .eventType(eventType)
                        .previousQuantity(currentQty)
                        .newQuantity(currentQty)
                        .status("REJECTED")
                        .message("Insufficient stock")
                        .build();
            }

            int newQty = currentQty + delta;

            // Atomic version update
            int updated = jdbcTemplate.update(
                    "UPDATE inventory SET quantity = ?, version = version + 1, updated_at = now() WHERE sku = ? AND version = ?",
                    newQty, sku, currentVersion
            );

            if (updated == 1) {
                // Success! Record ledger
                jdbcTemplate.update(
                        "INSERT INTO inventory_movements (sku, delta, quantity_after, reason, event_id, created_at) VALUES (?, ?, ?, ?, ?, now())",
                        sku, delta, newQty, "ORDER".equals(eventType) ? "ORDER" : "RESTOCK", eventId);

                recordOrder(eventId, sku, requestedQty, "COMPLETED", null);
                recordLog(eventId, eventType, sku, "INFO", "Optimistic update succeeded on attempt " + attempt);
                updateDsaAndAlert(sku, newQty, eventId, eventType);

                return ExecutionResult.builder()
                        .success(true)
                        .eventId(eventId)
                        .sku(sku)
                        .eventType(eventType)
                        .previousQuantity(currentQty)
                        .newQuantity(newQty)
                        .status("COMPLETED")
                        .message("Optimistic update success")
                        .build();
            }

            // Conflict! Jitter backoff before retry
            try {
                long sleepMs = (2L * (1L << Math.min(attempt, 5))) + ThreadLocalRandom.current().nextLong(3, 12);
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return failEvent(eventId, sku, eventType, "Interrupted during retry");
            }
        }

        // Retries exhausted
        recordOrder(eventId, sku, requestedQty, "FAILED", "Optimistic locking conflict exhausted retries");
        recordFailedEvent(eventId, "Optimistic locking conflict exhausted retries");
        return failEvent(eventId, sku, eventType, "Optimistic locking conflict exhausted retries");
    }

    private ExecutionResult processScan(StockEvent event) {
        recordLog(event.getEventId(), "WAREHOUSE_SCAN", event.getSku(), "INFO", "Warehouse scan verified for SKU " + event.getSku());
        return ExecutionResult.builder()
                .success(true)
                .eventId(event.getEventId())
                .sku(event.getSku())
                .eventType(event.getEventType())
                .previousQuantity(0)
                .newQuantity(0)
                .status("COMPLETED")
                .message("Scan recorded")
                .build();
    }

    private ExecutionResult processRestockFailed(StockEvent event) {
        String msg = "RESTOCK_FAILED: supplier delivery delayed for SKU " + event.getSku();
        recordLog(event.getEventId(), "RESTOCK_FAILED", event.getSku(), "ERROR", msg);

        // Notify alert service
        alertService.processEventForAlerts(
                event.getSku(), 0, 0, "RESTOCK_FAILED", event.getTimestamp(), 0, 1, 0, 0.0);

        return ExecutionResult.builder()
                .success(true)
                .eventId(event.getEventId())
                .sku(event.getSku())
                .eventType(event.getEventType())
                .previousQuantity(0)
                .newQuantity(0)
                .status("COMPLETED")
                .message("Restock failure logged and evaluated")
                .build();
    }

    private void updateDsaAndAlert(String sku, int newQty, String eventId, String eventType) {
        try {
            Inventory inv = inventoryRepository.findById(sku).orElse(null);
            int threshold = inv != null ? inv.getReorderThreshold() : 20;
            int reorderQty = inv != null ? inv.getReorderQuantity() : 50;
            long ver = inv != null ? inv.getVersion() : 0L;

            dsaEngine.getInventoryCache().put(sku, newQty, threshold, reorderQty, ver);
            dsaEngine.getFrequencyTracker().record(sku, eventType);

            alertService.processEventForAlerts(
                    sku, newQty, threshold, null, Instant.now(), 0, 0, 1, 1.0);
        } catch (Exception e) {
            log.warn("Error updating DSA or Alert for SKU {}: {}", sku, e.getMessage());
        }
    }

    private void recordOrder(String eventId, String sku, int qty, String status, String failureReason) {
        try {
            orderRepository.save(Order.builder()
                    .eventId(eventId)
                    .sku(sku)
                    .quantity(Math.max(1, qty))
                    .status(status)
                    .failureReason(failureReason)
                    .createdAt(Instant.now())
                    .processedAt(Instant.now())
                    .processedBy(Thread.currentThread().getName())
                    .build());
        } catch (Exception e) {
            log.debug("Order already recorded or constraint: {}", e.getMessage());
        }
    }

    private void recordLog(String eventId, String eventType, String sku, String level, String msg) {
        try {
            eventLogRepository.save(EventLog.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .sku(sku)
                    .level(level)
                    .message(msg)
                    .ingestedAt(Instant.now())
                    .createdAt(Instant.now())
                    .build());
        } catch (Exception e) {
            log.debug("Event log record error: {}", e.getMessage());
        }
    }

    private void recordFailedEvent(String eventId, String error) {
        try {
            failedEventRepository.save(FailedEvent.builder()
                    .eventId(eventId)
                    .payload("{\"eventId\":\"" + eventId + "\"}")
                    .error(error)
                    .attempts(1)
                    .createdAt(Instant.now())
                    .build());
        } catch (Exception e) {
            log.debug("Failed event record error: {}", e.getMessage());
        }
    }

    private ExecutionResult failEvent(String eventId, String sku, String eventType, String message) {
        return ExecutionResult.builder()
                .success(false)
                .eventId(eventId)
                .sku(sku)
                .eventType(eventType)
                .previousQuantity(-1)
                .newQuantity(-1)
                .status("FAILED")
                .message(message)
                .build();
    }
}
