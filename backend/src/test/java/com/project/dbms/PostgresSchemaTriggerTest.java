package com.project.dbms;

import com.project.config.EmbeddedPostgresConfig;
import com.project.dbms.entity.*;
import com.project.dbms.repository.*;
import com.project.dbms.service.ConsistencyCheckService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(EmbeddedPostgresConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("DBMS")
class PostgresSchemaTriggerTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryMovementRepository movementRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private ReorderRequestRepository reorderRepository;

    @Autowired
    private ConsistencyCheckService consistencyCheckService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Tag("NFR-02")
    @Tag("AC-08")
    @DisplayName("DBMS Constraint: Negative inventory quantity is strictly rejected by CHECK constraint")
    void testNegativeStockRejected() {
        assertThrows(Exception.class, () -> {
            jdbcTemplate.update("UPDATE inventory SET quantity = -5 WHERE sku = 'SKU-MILK-01'");
        });
    }

    @Test
    @Tag("NFR-03")
    @DisplayName("DBMS Constraint: Duplicate event_id in orders table is rejected by UNIQUE constraint")
    void testDuplicateOrderEventIdRejected() {
        Order o1 = Order.builder()
                .eventId("EVT-DUP-01")
                .sku("SKU-MILK-01")
                .quantity(2)
                .status("COMPLETED")
                .build();
        orderRepository.saveAndFlush(o1);

        Order o2 = Order.builder()
                .eventId("EVT-DUP-01") // Same event_id
                .sku("SKU-MILK-01")
                .quantity(3)
                .status("COMPLETED")
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            orderRepository.saveAndFlush(o2);
        });
    }

    @Test
    @Tag("AC-05")
    @DisplayName("DBMS Constraint: Second open reorder for the same vendor + SKU is rejected by partial unique index uq_open_reorder_per_vendor_sku")
    void testSecondOpenReorderRejected() {
        ReorderRequest r1 = ReorderRequest.builder()
                .sku("SKU-MILK-01")
                .vendorId(1)
                .quantity(100)
                .priorityScore(new BigDecimal("350.00"))
                .status("CREATED")
                .build();
        reorderRepository.saveAndFlush(r1);

        ReorderRequest r2 = ReorderRequest.builder()
                .sku("SKU-MILK-01") // Same vendor, same SKU, open status
                .vendorId(1)
                .quantity(100)
                .priorityScore(new BigDecimal("400.00"))
                .status("ACKNOWLEDGED")
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            reorderRepository.saveAndFlush(r2);
        });
    }

    @Test
    @DisplayName("DBMS Constraint: Second active alert for same SKU + pattern is rejected by uq_active_alert")
    void testSecondActiveAlertRejected() {
        Alert a1 = Alert.builder()
                .sku("SKU-MILK-01")
                .pattern("LOW_STOCK")
                .severity("HIGH")
                .status("OPEN")
                .build();
        alertRepository.saveAndFlush(a1);

        Alert a2 = Alert.builder()
                .sku("SKU-MILK-01")
                .pattern("LOW_STOCK")
                .severity("HIGH")
                .status("OPEN")
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            alertRepository.saveAndFlush(a2);
        });
    }

    @Test
    @DisplayName("DBMS Trigger: trg_reorder_guard blocks illegal status transitions (e.g. CREATED -> DELIVERED)")
    void testIllegalReorderTransitionBlocked() {
        ReorderRequest reorder = reorderRepository.saveAndFlush(ReorderRequest.builder()
                .sku("SKU-EGGS-02")
                .vendorId(1)
                .quantity(50)
                .priorityScore(new BigDecimal("300.00"))
                .status("CREATED")
                .build());

        // Illegal jump: CREATED cannot transition directly to DELIVERED
        assertThrows(Exception.class, () -> {
            jdbcTemplate.update("UPDATE reorder_requests SET status = 'DELIVERED' WHERE reorder_id = ?", reorder.getReorderId());
        });
    }

    @Test
    @Tag("NFR-20")
    @DisplayName("DBMS Trigger: fn_append_only blocks UPDATE and DELETE on inventory_movements, audit_log, and history")
    void testAppendOnlyLedgerTriggers() {
        InventoryMovement mov = movementRepository.saveAndFlush(InventoryMovement.builder()
                .sku("SKU-MILK-01")
                .delta(-1)
                .quantityAfter(119)
                .reason("ORDER")
                .eventId("EVT-TEST-AO")
                .build());

        // Attempt UPDATE on inventory_movements -> must fail
        assertThrows(Exception.class, () -> {
            jdbcTemplate.update("UPDATE inventory_movements SET delta = -10 WHERE id = ?", mov.getId());
        });

        // Attempt DELETE on inventory_movements -> must fail
        assertThrows(Exception.class, () -> {
            jdbcTemplate.update("DELETE FROM inventory_movements WHERE id = ?", mov.getId());
        });
    }

    @Test
    @DisplayName("Consistency Check: v_inventory_consistency is empty for properly seeded products")
    void testConsistencyCheckPasses() {
        ConsistencyCheckService.ConsistencyReport report = consistencyCheckService.runConsistencyCheck();
        assertTrue(report.isConsistent(), "Database should be 100% consistent after clean seeding");
        assertEquals(0, report.getInconsistencyCount());
    }

    @Test
    @DisplayName("DBMS Constraint: a different vendor may have its own open order for the same SKU")
    void testDifferentVendorsMayOrderSameSku() {
        reorderRepository.saveAndFlush(ReorderRequest.builder()
                .sku("SKU-MILK-01").vendorId(1).quantity(10)
                .priorityScore(new BigDecimal("100.00")).status("CREATED").build());

        ReorderRequest other = reorderRepository.saveAndFlush(ReorderRequest.builder()
                .sku("SKU-MILK-01").vendorId(2).quantity(10)
                .priorityScore(new BigDecimal("100.00")).status("CREATED").build());

        assertNotNull(other.getReorderId());
    }

    @Test
    @DisplayName("Manager/Vendor separation: manager products have no vendor column; vendor data lives in schema 'vendor'")
    void testManagerAndVendorDataAreSeparated() {
        Integer vendorColumnsOnProducts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'products' AND column_name = 'vendor_id'",
                Integer.class);
        assertEquals(0, vendorColumnsOnProducts);

        Integer vendorTablesInPublic = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('vendors','vendor_catalog')",
                Integer.class);
        assertEquals(0, vendorTablesInPublic);

        Integer vendorTables = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'vendor' AND table_name IN ('vendors','vendor_catalog')",
                Integer.class);
        assertEquals(2, vendorTables);
    }

    @Test
    @DisplayName("Seed: manager DB holds all 50 SKUs; each vendor has its own catalog rows")
    void testSeedShape() {
        Integer managerSkus = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Integer.class);
        assertEquals(50, managerSkus);

        Integer vendorRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vendor.vendor_catalog", Integer.class);
        assertEquals(50, vendorRows);
    }
}
