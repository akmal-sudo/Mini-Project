package com.project.web.controller;

import com.project.dbms.entity.ReorderRequest;
import com.project.dbms.repository.ReorderRequestRepository;
import com.project.reorder.ReorderService;
import com.project.web.dto.ReorderDtos.CancelReorderRequest;
import com.project.web.dto.ReorderDtos.CreateReorderRequest;
import com.project.web.dto.ReorderDtos.ReorderDto;
import com.project.web.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Vendor supply orders.
 *  - VENDOR           : places orders for ANY product of the manager catalog, confirms receipt, cancels own CREATED orders
 *  - MANAGER / ADMIN  : acknowledges and dispatches orders (dispatch takes stock out of manager inventory)
 * Managers see vendors only as an anonymous vendor id - never a vendor name or a vendor's catalog.
 */
@RestController
@RequestMapping("/api/reorders")
public class ReorderController {

    private final ReorderRequestRepository reorderRepository;
    private final ReorderService reorderService;

    public ReorderController(
            ReorderRequestRepository reorderRepository,
            ReorderService reorderService) {
        this.reorderRepository = reorderRepository;
        this.reorderService = reorderService;
    }

    @GetMapping
    public ResponseEntity<List<ReorderDto>> getReorders(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        List<ReorderRequest> list;
        if (isVendor(principal)) {
            // VENDOR can ONLY see its own orders
            list = reorderRepository.findByVendorIdOrderByCreatedAtDesc(principal.getVendorId());
            if (status != null && !status.isBlank()) {
                list = list.stream().filter(r -> r.getStatus().equalsIgnoreCase(status)).collect(Collectors.toList());
            }
        } else if (status != null && !status.isBlank()) {
            list = reorderRepository.findByStatusOrderByCreatedAtDesc(status.toUpperCase());
        } else {
            PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
            list = reorderRepository.findAll(pageRequest).getContent();
        }

        List<ReorderDto> dtos = list.stream().map(r -> ReorderDto.builder()
                .reorderId(r.getReorderId())
                .sku(r.getSku())
                .vendorId(r.getVendorId())
                .quantity(r.getQuantity())
                .priorityScore(r.getPriorityScore())
                .severity(r.getSeverity())
                .status(r.getStatus())
                .alertId(r.getAlertId())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .deliveredAt(r.getDeliveredAt())
                .build()
        ).collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /** A vendor orders a product from the manager catalog - it need not be in the vendor's own catalog. */
    @PostMapping
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ReorderRequest> createReorder(
            @Valid @RequestBody CreateReorderRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return reorderService.scheduleReorder(principal.getVendorId(), request.getSku(), request.getQuantity(), request.getSeverity())
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new IllegalArgumentException("SKU not found in manager catalog: " + request.getSku()));
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE_MANAGER')")
    public ResponseEntity<ReorderRequest> acknowledgeReorder(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(reorderService.acknowledgeReorder(id, userId(principal)));
    }

    @PostMapping("/{id}/ship")
    @PreAuthorize("hasAnyRole('ADMIN','WAREHOUSE_MANAGER')")
    public ResponseEntity<ReorderRequest> shipReorder(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(reorderService.shipReorder(id, userId(principal)));
    }

    /** The vendor confirms it received the goods: this is what raises the VENDOR's stock count. */
    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasAnyRole('ADMIN','VENDOR')")
    public ResponseEntity<ReorderRequest> deliverReorder(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        verifyVendorAccess(id, principal);
        return ResponseEntity.ok(reorderService.deliverReorder(id, userId(principal)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ReorderRequest> cancelReorder(
            @PathVariable Long id,
            @Valid @RequestBody CancelReorderRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ReorderRequest r = verifyVendorAccess(id, principal);
        if (isVendor(principal) && !"CREATED".equals(r.getStatus())) {
            throw new IllegalStateException("Vendors can only cancel an order before the manager acknowledges it");
        }
        return ResponseEntity.ok(reorderService.cancelReorder(id, userId(principal), request.getReason()));
    }

    private ReorderRequest verifyVendorAccess(Long reorderId, UserPrincipal principal) {
        ReorderRequest r = reorderRepository.findById(reorderId)
                .orElseThrow(() -> new IllegalArgumentException("Reorder not found: " + reorderId));
        if (isVendor(principal) && !Objects.equals(r.getVendorId(), principal.getVendorId())) {
            throw new AccessDeniedException("Vendors can only access their own orders");
        }
        return r;
    }

    private static boolean isVendor(UserPrincipal principal) {
        return principal != null && "VENDOR".equals(principal.getRole()) && principal.getVendorId() != null;
    }

    private static Integer userId(UserPrincipal principal) {
        return principal != null ? principal.getUserId() : null;
    }
}
