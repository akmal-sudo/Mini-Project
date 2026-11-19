package com.project.reorder;

import com.project.dbms.entity.*;
import com.project.dbms.repository.*;
import com.project.dsa.DsaEngine;
import com.project.dsa.ReorderPriorityHeap;
import com.project.web.websocket.WebSocketPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Vendor supply-order lifecycle (vendor orders FROM the manager catalog):
 * CREATED (vendor orders) -> ACKNOWLEDGED (manager) -> SHIPPED (manager stock decreases)
 *   -> DELIVERED (vendor confirms receipt, vendor stock increases) | CANCELLED
 * Manager stock is replenished separately by the manager (InventoryController).
 */
@Service
public class ReorderService {

    private static final Logger log = LoggerFactory.getLogger(ReorderService.class);

    private final ReorderRequestRepository reorderRepository;
    private final ReorderStatusHistoryRepository historyRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final AuditLogRepository auditLogRepository;
    private final VendorCatalogRepository vendorCatalogRepository;
    private final DsaEngine dsaEngine;
    private final WebSocketPublisher webSocketPublisher;

    public ReorderService(
            ReorderRequestRepository reorderRepository,
            ReorderStatusHistoryRepository historyRepository,
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            InventoryMovementRepository movementRepository,
            AuditLogRepository auditLogRepository,
            VendorCatalogRepository vendorCatalogRepository,
            DsaEngine dsaEngine) {
        this(reorderRepository, historyRepository, productRepository, inventoryRepository,
                movementRepository, auditLogRepository, vendorCatalogRepository, dsaEngine, null);
    }

    @Autowired
    public ReorderService(
            ReorderRequestRepository reorderRepository,
            ReorderStatusHistoryRepository historyRepository,
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            InventoryMovementRepository movementRepository,
            AuditLogRepository auditLogRepository,
            VendorCatalogRepository vendorCatalogRepository,
            DsaEngine dsaEngine,
            @Lazy WebSocketPublisher webSocketPublisher) {
        this.reorderRepository = reorderRepository;
        this.historyRepository = historyRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.auditLogRepository = auditLogRepository;
        this.vendorCatalogRepository = vendorCatalogRepository;
        this.dsaEngine = dsaEngine;
        this.webSocketPublisher = webSocketPublisher;
    }

    /**
     * A vendor orders a SKU from the manager catalog. The SKU does not have to be in the
     * vendor's own catalog. At most one open order per (vendor, SKU).
     */
    @Transactional
    public synchronized Optional<ReorderRequest> scheduleReorder(Integer vendorId, String sku, Integer quantity, String severity) {
        if (vendorId == null || sku == null) return Optional.empty();

        Optional<ReorderRequest> existing = reorderRepository.findByVendorIdAndSkuAndStatusIn(
                vendorId, sku, List.of("CREATED", "ACKNOWLEDGED", "SHIPPED"));
        if (existing.isPresent()) {
            return existing;
        }

        Product product = productRepository.findById(sku).orElse(null);
        Inventory inventory = inventoryRepository.findById(sku).orElse(null);
        if (product == null || inventory == null) {
            return Optional.empty();
        }

        int orderQty = (quantity != null && quantity > 0) ? quantity : inventory.getReorderQuantity();
        Long alertId = null;

        long freq = dsaEngine.getFrequencyTracker().getCountForSku(sku);
        BigDecimal priorityScore = ReorderCalculator.calculatePriorityScore(
                severity, inventory.getQuantity(), inventory.getReorderThreshold(), freq);

        ReorderRequest request = ReorderRequest.builder()
                .sku(sku)
                .vendorId(vendorId)
                .quantity(orderQty)
                .priorityScore(priorityScore)
                .severity(severity != null ? severity : "MEDIUM")
                .status("CREATED")
                .alertId(alertId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        ReorderRequest saved = reorderRepository.saveAndFlush(request);

        // Record history and audit log
        historyRepository.save(ReorderStatusHistory.builder()
                .reorderId(saved.getReorderId())
                .oldStatus(null)
                .newStatus("CREATED")
                .changedBy(null)
                .changedAt(Instant.now())
                .build());

        auditLogRepository.save(AuditLog.builder()
                .action("REORDER_CREATED")
                .entity("reorder_requests")
                .entityId(saved.getReorderId().toString())
                .details(String.format("{\"vendorId\":%d,\"sku\":\"%s\",\"qty\":%d,\"priority\":%s}", vendorId, sku, saved.getQuantity(), priorityScore))
                .createdAt(Instant.now())
                .build());

        // Enqueue into DSA Max-Heap
        dsaEngine.getPriorityHeap().enqueue(new ReorderPriorityHeap.PrioritizedReorder(
                sku, vendorId, saved.getQuantity(), priorityScore, severity, alertId, Instant.now()
        ));

        if (webSocketPublisher != null) {
            webSocketPublisher.publishReorder(saved);
        }

        log.info("Created reorder #{} for SKU {} with priority score {}", saved.getReorderId(), sku, priorityScore);
        return Optional.of(saved);
    }

    @Transactional
    public ReorderRequest acknowledgeReorder(Long reorderId, Integer userId) {
        ReorderRequest reorder = loadInStatus(reorderId, "CREATED", "acknowledged");
        String oldStatus = reorder.getStatus();
        reorder.setStatus("ACKNOWLEDGED");
        reorder.setUpdatedAt(Instant.now());
        ReorderRequest updated = reorderRepository.saveAndFlush(reorder);

        recordTransition(reorderId, oldStatus, "ACKNOWLEDGED", userId, "REORDER_ACKNOWLEDGED");
        return updated;
    }

    /**
     * Manager dispatches the supplies: manager stock DECREASES (append-only VENDOR_SUPPLY ledger row).
     * Fails if the manager does not hold enough units - the manager must replenish first.
     */
    @Transactional
    public ReorderRequest shipReorder(Long reorderId, Integer userId) {
        ReorderRequest reorder = loadInStatus(reorderId, "ACKNOWLEDGED", "dispatched");

        Inventory inventory = inventoryRepository.findByIdWithPessimisticLock(reorder.getSku())
                .orElseThrow(() -> new IllegalStateException("Inventory not found for SKU: " + reorder.getSku()));
        int oldQty = inventory.getQuantity();
        if (oldQty < reorder.getQuantity()) {
            throw new IllegalArgumentException("Insufficient manager stock for " + reorder.getSku()
                    + ": need " + reorder.getQuantity() + ", have " + oldQty + ". Replenish the SKU first.");
        }
        int newQty = oldQty - reorder.getQuantity();
        inventory.setQuantity(newQty);
        inventoryRepository.saveAndFlush(inventory);

        movementRepository.save(InventoryMovement.builder()
                .sku(reorder.getSku())
                .delta(-reorder.getQuantity())
                .quantityAfter(newQty)
                .reason("VENDOR_SUPPLY")
                .eventId("SUPPLY-" + reorderId)
                .createdAt(Instant.now())
                .build());
        dsaEngine.getInventoryCache().put(
                reorder.getSku(), newQty, inventory.getReorderThreshold(), inventory.getReorderQuantity(), inventory.getVersion());

        String oldStatus = reorder.getStatus();
        reorder.setStatus("SHIPPED");
        reorder.setUpdatedAt(Instant.now());
        ReorderRequest updated = reorderRepository.saveAndFlush(reorder);

        recordTransition(reorderId, oldStatus, "SHIPPED", userId, "REORDER_SHIPPED");
        publishInventory(reorder.getSku(), newQty, -reorder.getQuantity(), "VENDOR_SUPPLY");
        log.info("Dispatched reorder #{}: manager stock of {} -{} -> {}", reorderId, reorder.getSku(), reorder.getQuantity(), newQty);
        return updated;
    }

    /**
     * Vendor confirms receipt: the VENDOR's own stock count increases. If the SKU was not in the
     * vendor's catalog yet it is added (source MANAGER_CATALOG). Manager stock is untouched here.
     */
    @Transactional
    public ReorderRequest deliverReorder(Long reorderId, Integer userId) {
        ReorderRequest reorder = loadInStatus(reorderId, "SHIPPED", "delivered");

        Product product = productRepository.findById(reorder.getSku())
                .orElseThrow(() -> new IllegalStateException("SKU no longer in manager catalog: " + reorder.getSku()));

        VendorCatalogItem item = vendorCatalogRepository.findForUpdate(reorder.getVendorId(), reorder.getSku()).orElse(null);
        if (item == null) {
            item = VendorCatalogItem.builder()
                    .vendorId(reorder.getVendorId())
                    .sku(product.getSku())
                    .name(product.getName())
                    .category(product.getCategory())
                    .price(product.getPrice())
                    .quantity(0)
                    .source("MANAGER_CATALOG")
                    .createdAt(Instant.now())
                    .build();
        }
        item.setQuantity(item.getQuantity() + reorder.getQuantity());
        vendorCatalogRepository.saveAndFlush(item);

        String oldStatus = reorder.getStatus();
        reorder.setStatus("DELIVERED");
        reorder.setDeliveredAt(Instant.now());
        reorder.setUpdatedAt(Instant.now());
        ReorderRequest updated = reorderRepository.saveAndFlush(reorder);

        recordTransition(reorderId, oldStatus, "DELIVERED", userId, "REORDER_DELIVERED");
        log.info("Delivered reorder #{}: vendor {} stock of {} +{} -> {}", reorderId, reorder.getVendorId(),
                reorder.getSku(), reorder.getQuantity(), item.getQuantity());
        return updated;
    }

    @Transactional
    public ReorderRequest cancelReorder(Long reorderId, Integer userId, String reason) {
        ReorderRequest reorder = reorderRepository.findById(reorderId)
                .orElseThrow(() -> new IllegalArgumentException("Reorder not found: " + reorderId));
        String oldStatus = reorder.getStatus();
        if ("DELIVERED".equals(oldStatus) || "CANCELLED".equals(oldStatus)) {
            throw new IllegalStateException("Reorder #" + reorderId + " is already " + oldStatus + " and cannot be cancelled");
        }

        // stock already left the manager at dispatch: put it back, ledger-consistently
        if ("SHIPPED".equals(oldStatus)) {
            Inventory inventory = inventoryRepository.findByIdWithPessimisticLock(reorder.getSku())
                    .orElseThrow(() -> new IllegalStateException("Inventory not found for SKU: " + reorder.getSku()));
            int newQty = inventory.getQuantity() + reorder.getQuantity();
            inventory.setQuantity(newQty);
            inventoryRepository.saveAndFlush(inventory);
            movementRepository.save(InventoryMovement.builder()
                    .sku(reorder.getSku())
                    .delta(reorder.getQuantity())
                    .quantityAfter(newQty)
                    .reason("CANCEL")
                    .eventId("SUPPLY-CANCEL-" + reorderId)
                    .createdAt(Instant.now())
                    .build());
            dsaEngine.getInventoryCache().put(
                    reorder.getSku(), newQty, inventory.getReorderThreshold(), inventory.getReorderQuantity(), inventory.getVersion());
            publishInventory(reorder.getSku(), newQty, reorder.getQuantity(), "CANCEL");
        }

        reorder.setStatus("CANCELLED");
        reorder.setUpdatedAt(Instant.now());
        ReorderRequest updated = reorderRepository.saveAndFlush(reorder);

        recordTransition(reorderId, oldStatus, "CANCELLED", userId, "REORDER_CANCELLED (" + reason + ")");
        return updated;
    }

    private ReorderRequest loadInStatus(Long reorderId, String expected, String verb) {
        ReorderRequest reorder = reorderRepository.findById(reorderId)
                .orElseThrow(() -> new IllegalArgumentException("Reorder not found: " + reorderId));
        if (!expected.equals(reorder.getStatus())) {
            throw new IllegalStateException("Reorder #" + reorderId + " is " + reorder.getStatus()
                    + "; it can only be " + verb + " from " + expected);
        }
        return reorder;
    }

    private void publishInventory(String sku, int quantity, int delta, String reason) {
        if (webSocketPublisher != null) {
            webSocketPublisher.publishInventoryUpdate(Map.of(
                    "sku", sku, "quantity", quantity, "delta", delta, "reason", reason));
        }
    }

    private void recordTransition(Long reorderId, String oldStatus, String newStatus, Integer userId, String action) {
        historyRepository.save(ReorderStatusHistory.builder()
                .reorderId(reorderId)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .changedBy(userId)
                .changedAt(Instant.now())
                .build());

        auditLogRepository.save(AuditLog.builder()
                .userId(userId)
                .action(action)
                .entity("reorder_requests")
                .entityId(reorderId.toString())
                .details(String.format("{\"from\":\"%s\",\"to\":\"%s\"}", oldStatus, newStatus))
                .createdAt(Instant.now())
                .build());

        if (webSocketPublisher != null) {
            reorderRepository.findById(reorderId).ifPresent(webSocketPublisher::publishReorder);
        }
    }
}
