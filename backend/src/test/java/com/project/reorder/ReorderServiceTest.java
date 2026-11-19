package com.project.reorder;

import com.project.dbms.entity.*;
import com.project.dbms.repository.*;
import com.project.dsa.DsaEngine;
import com.project.dsa.FrequencyTracker;
import com.project.dsa.InventoryCache;
import com.project.dsa.ReorderPriorityHeap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("REORDER")
class ReorderServiceTest {

    private ReorderRequestRepository reorderRepository;
    private ReorderStatusHistoryRepository historyRepository;
    private ProductRepository productRepository;
    private InventoryRepository inventoryRepository;
    private InventoryMovementRepository movementRepository;
    private AuditLogRepository auditLogRepository;
    private VendorCatalogRepository vendorCatalogRepository;
    private DsaEngine dsaEngine;
    private FrequencyTracker frequencyTracker;
    private ReorderPriorityHeap priorityHeap;
    private InventoryCache inventoryCache;

    private ReorderService reorderService;

    @BeforeEach
    void setUp() {
        reorderRepository = mock(ReorderRequestRepository.class);
        historyRepository = mock(ReorderStatusHistoryRepository.class);
        productRepository = mock(ProductRepository.class);
        inventoryRepository = mock(InventoryRepository.class);
        movementRepository = mock(InventoryMovementRepository.class);
        auditLogRepository = mock(AuditLogRepository.class);
        vendorCatalogRepository = mock(VendorCatalogRepository.class);
        dsaEngine = mock(DsaEngine.class);
        frequencyTracker = mock(FrequencyTracker.class);
        priorityHeap = mock(ReorderPriorityHeap.class);
        inventoryCache = mock(InventoryCache.class);

        when(dsaEngine.getFrequencyTracker()).thenReturn(frequencyTracker);
        when(dsaEngine.getPriorityHeap()).thenReturn(priorityHeap);
        when(dsaEngine.getInventoryCache()).thenReturn(inventoryCache);

        reorderService = new ReorderService(
                reorderRepository,
                historyRepository,
                productRepository,
                inventoryRepository,
                movementRepository,
                auditLogRepository,
                vendorCatalogRepository,
                dsaEngine
        );
    }

    @Test
    @DisplayName("A vendor can order any manager-catalog SKU; request is created and enqueued")
    void testScheduleReorderSuccess() {
        when(reorderRepository.findByVendorIdAndSkuAndStatusIn(eq(10), eq("SKU-1"), anyList())).thenReturn(Optional.empty());

        Product product = Product.builder().sku("SKU-1").price(new BigDecimal("9.99")).build();
        Inventory inventory = Inventory.builder().sku("SKU-1").quantity(1000).reorderThreshold(20).reorderQuantity(50).version(1L).build();

        when(productRepository.findById("SKU-1")).thenReturn(Optional.of(product));
        when(inventoryRepository.findById("SKU-1")).thenReturn(Optional.of(inventory));
        when(frequencyTracker.getCountForSku("SKU-1")).thenReturn(5L);

        when(reorderRepository.saveAndFlush(any(ReorderRequest.class))).thenAnswer(inv -> {
            ReorderRequest req = inv.getArgument(0);
            req.setReorderId(123L);
            return req;
        });

        Optional<ReorderRequest> result = reorderService.scheduleReorder(10, "SKU-1", 75, "HIGH");

        assertTrue(result.isPresent());
        assertEquals(123L, result.get().getReorderId());
        assertEquals(10, result.get().getVendorId());
        assertEquals(75, result.get().getQuantity());
        assertEquals("CREATED", result.get().getStatus());

        verify(priorityHeap).enqueue(any());
        verify(historyRepository).save(any());
        verify(auditLogRepository).save(any());
    }

    @Test
    @DisplayName("Unknown SKU (not in manager catalog) creates nothing")
    void testScheduleReorderUnknownSku() {
        when(reorderRepository.findByVendorIdAndSkuAndStatusIn(eq(10), eq("NOPE"), anyList())).thenReturn(Optional.empty());
        when(productRepository.findById("NOPE")).thenReturn(Optional.empty());

        assertTrue(reorderService.scheduleReorder(10, "NOPE", 5, "HIGH").isEmpty());
        verify(reorderRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Returns the existing open order if this vendor already has one for the SKU")
    void testScheduleReorderAlreadyOpen() {
        ReorderRequest existing = ReorderRequest.builder().reorderId(50L).sku("SKU-1").vendorId(10).status("CREATED").build();
        when(reorderRepository.findByVendorIdAndSkuAndStatusIn(eq(10), eq("SKU-1"), anyList())).thenReturn(Optional.of(existing));

        Optional<ReorderRequest> result = reorderService.scheduleReorder(10, "SKU-1", 20, "CRITICAL");

        assertTrue(result.isPresent());
        assertEquals(50L, result.get().getReorderId());
        verify(reorderRepository, never()).saveAndFlush(any());
        verify(priorityHeap, never()).enqueue(any());
    }

    @Test
    @DisplayName("Acknowledge moves CREATED -> ACKNOWLEDGED and does not touch stock")
    void testAcknowledge() {
        ReorderRequest req = ReorderRequest.builder().reorderId(1L).sku("SKU-1").vendorId(10).quantity(50).status("CREATED").build();
        when(reorderRepository.findById(1L)).thenReturn(Optional.of(req));
        when(reorderRepository.saveAndFlush(any(ReorderRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ReorderRequest acked = reorderService.acknowledgeReorder(1L, 7);

        assertEquals("ACKNOWLEDGED", acked.getStatus());
        verifyNoInteractions(inventoryRepository, movementRepository);
    }

    @Test
    @DisplayName("Illegal transition (ship straight from CREATED) is rejected")
    void testShipRequiresAcknowledged() {
        ReorderRequest req = ReorderRequest.builder().reorderId(1L).sku("SKU-1").vendorId(10).quantity(50).status("CREATED").build();
        when(reorderRepository.findById(1L)).thenReturn(Optional.of(req));

        assertThrows(IllegalStateException.class, () -> reorderService.shipReorder(1L, 7));
        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ship decreases MANAGER stock and writes a VENDOR_SUPPLY ledger row")
    void testShipDecreasesManagerStock() {
        ReorderRequest req = ReorderRequest.builder().reorderId(1L).sku("SKU-1").vendorId(10).quantity(50).status("ACKNOWLEDGED").build();
        when(reorderRepository.findById(1L)).thenReturn(Optional.of(req));
        when(reorderRepository.saveAndFlush(any(ReorderRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        Inventory inventory = Inventory.builder().sku("SKU-1").quantity(1000).reorderThreshold(20).reorderQuantity(50).version(1L).build();
        when(inventoryRepository.findByIdWithPessimisticLock("SKU-1")).thenReturn(Optional.of(inventory));

        ReorderRequest shipped = reorderService.shipReorder(1L, 7);

        assertEquals("SHIPPED", shipped.getStatus());
        assertEquals(950, inventory.getQuantity());
        verify(movementRepository).save(argThat(m -> m.getDelta() == -50 && "VENDOR_SUPPLY".equals(m.getReason()) && m.getQuantityAfter() == 950));
        verify(inventoryCache).put("SKU-1", 950, 20, 50, 1L);
    }

    @Test
    @DisplayName("Ship is refused when the manager does not hold enough units")
    void testShipInsufficientManagerStock() {
        ReorderRequest req = ReorderRequest.builder().reorderId(1L).sku("SKU-1").vendorId(10).quantity(500).status("ACKNOWLEDGED").build();
        when(reorderRepository.findById(1L)).thenReturn(Optional.of(req));
        Inventory inventory = Inventory.builder().sku("SKU-1").quantity(100).reorderThreshold(20).reorderQuantity(50).version(1L).build();
        when(inventoryRepository.findByIdWithPessimisticLock("SKU-1")).thenReturn(Optional.of(inventory));

        assertThrows(IllegalArgumentException.class, () -> reorderService.shipReorder(1L, 7));
        assertEquals(100, inventory.getQuantity());
        verify(movementRepository, never()).save(any());
        assertEquals("ACKNOWLEDGED", req.getStatus());
    }

    @Test
    @DisplayName("Deliver increases the VENDOR's stock and leaves manager stock alone")
    void testDeliverIncreasesVendorStock() {
        ReorderRequest req = ReorderRequest.builder().reorderId(1L).sku("SKU-1").vendorId(10).quantity(50).status("SHIPPED").build();
        when(reorderRepository.findById(1L)).thenReturn(Optional.of(req));
        when(reorderRepository.saveAndFlush(any(ReorderRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        Product product = Product.builder().sku("SKU-1").name("Milk").category("Dairy").price(new BigDecimal("3.49")).build();
        when(productRepository.findById("SKU-1")).thenReturn(Optional.of(product));

        VendorCatalogItem item = VendorCatalogItem.builder().vendorId(10).sku("SKU-1").name("Milk").price(new BigDecimal("3.49")).quantity(120).build();
        when(vendorCatalogRepository.findForUpdate(10, "SKU-1")).thenReturn(Optional.of(item));

        ReorderRequest delivered = reorderService.deliverReorder(1L, 7);

        assertEquals("DELIVERED", delivered.getStatus());
        assertEquals(170, item.getQuantity());
        verify(vendorCatalogRepository).saveAndFlush(item);
        verifyNoInteractions(inventoryRepository, movementRepository);
    }

    @Test
    @DisplayName("Deliver of a SKU outside the vendor's catalog adds it to that catalog")
    void testDeliverAddsNewSkuToVendorCatalog() {
        ReorderRequest req = ReorderRequest.builder().reorderId(1L).sku("SKU-9").vendorId(10).quantity(30).status("SHIPPED").build();
        when(reorderRepository.findById(1L)).thenReturn(Optional.of(req));
        when(reorderRepository.saveAndFlush(any(ReorderRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        Product product = Product.builder().sku("SKU-9").name("Cheddar").category("Dairy").price(new BigDecimal("5.19")).build();
        when(productRepository.findById("SKU-9")).thenReturn(Optional.of(product));
        when(vendorCatalogRepository.findForUpdate(10, "SKU-9")).thenReturn(Optional.empty());

        reorderService.deliverReorder(1L, 7);

        verify(vendorCatalogRepository).saveAndFlush(argThat(i ->
                i.getVendorId() == 10 && "SKU-9".equals(i.getSku()) && i.getQuantity() == 30 && "MANAGER_CATALOG".equals(i.getSource())));
    }

    @Test
    @DisplayName("Cancelling a CREATED order only writes an audit log")
    void testCancelCreated() {
        ReorderRequest req = ReorderRequest.builder().reorderId(2L).sku("SKU-2").vendorId(10).quantity(5).status("CREATED").build();
        when(reorderRepository.findById(2L)).thenReturn(Optional.of(req));
        when(reorderRepository.saveAndFlush(any(ReorderRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ReorderRequest cancelled = reorderService.cancelReorder(2L, 5, "Duplicate order");

        assertEquals("CANCELLED", cancelled.getStatus());
        verify(auditLogRepository).save(argThat(a -> a.getAction().contains("CANCELLED")));
        verifyNoInteractions(inventoryRepository, movementRepository);
    }

    @Test
    @DisplayName("Cancelling a SHIPPED order puts the units back into manager stock")
    void testCancelShippedRestoresManagerStock() {
        ReorderRequest req = ReorderRequest.builder().reorderId(3L).sku("SKU-1").vendorId(10).quantity(50).status("SHIPPED").build();
        when(reorderRepository.findById(3L)).thenReturn(Optional.of(req));
        when(reorderRepository.saveAndFlush(any(ReorderRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        Inventory inventory = Inventory.builder().sku("SKU-1").quantity(950).reorderThreshold(20).reorderQuantity(50).version(1L).build();
        when(inventoryRepository.findByIdWithPessimisticLock("SKU-1")).thenReturn(Optional.of(inventory));

        reorderService.cancelReorder(3L, 5, "Changed mind");

        assertEquals(1000, inventory.getQuantity());
        verify(movementRepository).save(argThat(m -> m.getDelta() == 50 && "CANCEL".equals(m.getReason())));
    }

    @Test
    @DisplayName("A DELIVERED order can no longer be cancelled")
    void testCancelDeliveredRejected() {
        ReorderRequest req = ReorderRequest.builder().reorderId(4L).sku("SKU-1").vendorId(10).quantity(50).status("DELIVERED").build();
        when(reorderRepository.findById(4L)).thenReturn(Optional.of(req));

        assertThrows(IllegalStateException.class, () -> reorderService.cancelReorder(4L, 5, "late"));
    }
}
