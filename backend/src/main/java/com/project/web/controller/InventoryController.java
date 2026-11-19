package com.project.web.controller;

import com.project.dbms.entity.Alert;
import com.project.dbms.entity.AuditLog;
import com.project.dbms.entity.Inventory;
import com.project.dbms.entity.InventoryMovement;
import com.project.dbms.entity.Product;
import com.project.dbms.repository.AlertRepository;
import com.project.dbms.repository.AuditLogRepository;
import com.project.dbms.repository.InventoryMovementRepository;
import com.project.dbms.repository.InventoryRepository;
import com.project.dbms.repository.ProductRepository;
import com.project.dsa.DsaEngine;
import com.project.web.dto.InventoryDtos.AdjustStockRequest;
import com.project.web.dto.InventoryDtos.InventoryDto;
import com.project.web.dto.InventoryDtos.ReplenishRequest;
import com.project.web.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * MANAGER stock (the manager DB): every SKU, no vendor information.
 * Only the warehouse manager / admin can see or change it.
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final InventoryMovementRepository movementRepository;
    private final AuditLogRepository auditLogRepository;
    private final AlertRepository alertRepository;
    private final DsaEngine dsaEngine;

    public InventoryController(
            InventoryRepository inventoryRepository,
            ProductRepository productRepository,
            InventoryMovementRepository movementRepository,
            AuditLogRepository auditLogRepository,
            AlertRepository alertRepository,
            DsaEngine dsaEngine) {
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.movementRepository = movementRepository;
        this.auditLogRepository = auditLogRepository;
        this.alertRepository = alertRepository;
        this.dsaEngine = dsaEngine;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE_MANAGER')")
    public ResponseEntity<List<InventoryDto>> getInventory(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search) {

        List<Product> products = new ArrayList<>(productRepository.findAll());
        products.sort(Comparator.comparing(Product::getSku));

        List<InventoryDto> list = new ArrayList<>();
        for (Product p : products) {
            if (category != null && !category.isBlank() && !p.getCategory().equalsIgnoreCase(category)) {
                continue;
            }
            if (search != null && !search.isBlank()) {
                String term = search.toLowerCase();
                if (!p.getSku().toLowerCase().contains(term) && !p.getName().toLowerCase().contains(term)) {
                    continue;
                }
            }

            Inventory inv = inventoryRepository.findById(p.getSku()).orElse(null);
            int qty = inv != null ? inv.getQuantity() : 0;
            int threshold = inv != null ? inv.getReorderThreshold() : 0;
            int reorderQty = inv != null ? inv.getReorderQuantity() : 0;
            int initQty = inv != null ? inv.getInitialQuantity() : 0;
            long ver = inv != null ? inv.getVersion() : 0;
            Instant updated = inv != null ? inv.getUpdatedAt() : p.getCreatedAt();

            String stockStatus = qty == 0 ? "OUT_OF_STOCK" : (qty <= threshold ? "LOW" : "OK");
            if (status != null && !status.isBlank() && !status.equalsIgnoreCase(stockStatus)) {
                continue;
            }

            list.add(InventoryDto.builder()
                    .sku(p.getSku())
                    .name(p.getName())
                    .category(p.getCategory())
                    .price(p.getPrice())
                    .quantity(qty)
                    .initialQuantity(initQty)
                    .reorderThreshold(threshold)
                    .reorderQuantity(reorderQty)
                    .status(stockStatus)
                    .version(ver)
                    .updatedAt(updated)
                    .build());
        }

        return ResponseEntity.ok(list);
    }

    @GetMapping("/{sku}")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE_MANAGER')")
    public ResponseEntity<InventoryDto> getInventoryItem(
            @PathVariable String sku,
            @AuthenticationPrincipal UserPrincipal principal) {

        Product p = productRepository.findById(sku)
                .orElseThrow(() -> new IllegalArgumentException("SKU not found: " + sku));

        Inventory inv = inventoryRepository.findById(sku).orElse(null);
        int qty = inv != null ? inv.getQuantity() : 0;
        int threshold = inv != null ? inv.getReorderThreshold() : 0;
        String stockStatus = qty == 0 ? "OUT_OF_STOCK" : (qty <= threshold ? "LOW" : "OK");

        return ResponseEntity.ok(InventoryDto.builder()
                .sku(p.getSku())
                .name(p.getName())
                .category(p.getCategory())
                .price(p.getPrice())
                .quantity(qty)
                .initialQuantity(inv != null ? inv.getInitialQuantity() : 0)
                .reorderThreshold(threshold)
                .reorderQuantity(inv != null ? inv.getReorderQuantity() : 0)
                .status(stockStatus)
                .version(inv != null ? inv.getVersion() : 0)
                .updatedAt(inv != null ? inv.getUpdatedAt() : Instant.now())
                .build());
    }

    @PostMapping("/{sku}/adjust")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<InventoryDto> adjustStock(
            @PathVariable String sku,
            @Valid @RequestBody AdjustStockRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        Inventory inventory = inventoryRepository.findByIdWithPessimisticLock(sku)
                .orElseThrow(() -> new IllegalArgumentException("SKU not found: " + sku));

        int newQty = inventory.getQuantity() + request.getDelta();
        if (newQty < 0) {
            throw new IllegalArgumentException("Stock adjustment would result in negative quantity (" + newQty + ")");
        }

        inventory.setQuantity(newQty);
        inventoryRepository.saveAndFlush(inventory);

        // Record movement ledger
        movementRepository.save(InventoryMovement.builder()
                .sku(sku)
                .delta(request.getDelta())
                .quantityAfter(newQty)
                .reason("ADMIN_ADJUST")
                .eventId("ADJUST-" + UUID.randomUUID())
                .createdAt(Instant.now())
                .build());

        // Update DSA cache
        dsaEngine.getInventoryCache().put(
                sku, newQty, inventory.getReorderThreshold(), inventory.getReorderQuantity(), inventory.getVersion()
        );

        // Record audit log
        auditLogRepository.save(AuditLog.builder()
                .userId(principal != null ? principal.getUserId() : null)
                .action("ADMIN_STOCK_ADJUST")
                .entity("inventory")
                .entityId(sku)
                .details(String.format("{\"delta\":%d,\"newQty\":%d,\"reason\":\"%s\"}",
                        request.getDelta(), newQty, request.getReason()))
                .createdAt(Instant.now())
                .build());

        return getInventoryItem(sku, principal);
    }

    /**
     * Manager replenishes a SKU directly (no vendor involved): stock goes UP, one RESTOCK ledger row
     * is appended in the same transaction, and open low-stock alerts clear once above threshold.
     */
    @PostMapping("/{sku}/replenish")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE_MANAGER')")
    @Transactional
    public ResponseEntity<InventoryDto> replenish(
            @PathVariable String sku,
            @Valid @RequestBody ReplenishRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        Inventory inventory = inventoryRepository.findByIdWithPessimisticLock(sku)
                .orElseThrow(() -> new IllegalArgumentException("SKU not found: " + sku));

        int newQty = inventory.getQuantity() + request.getQuantity();
        inventory.setQuantity(newQty);
        inventoryRepository.saveAndFlush(inventory);

        movementRepository.save(InventoryMovement.builder()
                .sku(sku)
                .delta(request.getQuantity())
                .quantityAfter(newQty)
                .reason("RESTOCK")
                .eventId("REPLENISH-" + UUID.randomUUID())
                .createdAt(Instant.now())
                .build());

        dsaEngine.getInventoryCache().put(
                sku, newQty, inventory.getReorderThreshold(), inventory.getReorderQuantity(), inventory.getVersion()
        );

        if (newQty > inventory.getReorderThreshold()) {
            for (String st : List.of("OPEN", "ACKNOWLEDGED")) {
                for (Alert alert : alertRepository.findByStatusOrderByCreatedAtDesc(st)) {
                    if (sku.equals(alert.getSku())) {
                        alert.setStatus("RESOLVED");
                        alert.setResolvedAt(Instant.now());
                        alertRepository.save(alert);
                    }
                }
            }
        }

        auditLogRepository.save(AuditLog.builder()
                .userId(principal != null ? principal.getUserId() : null)
                .action("MANAGER_REPLENISH")
                .entity("inventory")
                .entityId(sku)
                .details(String.format("{\"added\":%d,\"newQty\":%d}", request.getQuantity(), newQty))
                .createdAt(Instant.now())
                .build());

        return getInventoryItem(sku, principal);
    }
}
