package com.project.dbms.service;

import lombok.Builder;
import lombok.Getter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * DBMS Engine Service verifying strict database consistency,
 * auditing v_inventory_consistency view, negative stock invariants,
 * ledger continuity, and reorder exclusivity.
 */
@Service
public class ConsistencyCheckService {

    private final JdbcTemplate jdbcTemplate;

    public ConsistencyCheckService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Getter
    @Builder
    public static class ConsistencyReport {
        private final boolean consistent;
        private final List<String> violations;
        private final int inconsistencyCount;
        private final Instant timestamp;
    }

    public ConsistencyReport runConsistencyCheck() {
        List<String> violations = new ArrayList<>();

        // 1. Audit v_inventory_consistency view: expected = initial + sum(delta) vs actual
        try {
            jdbcTemplate.query(
                    "SELECT sku, actual, expected FROM v_inventory_consistency",
                    rs -> {
                        String sku = rs.getString("sku");
                        int actual = rs.getInt("actual");
                        int expected = rs.getInt("expected");
                        violations.add("Ledger mismatch for SKU " + sku + ": actual=" + actual + " vs expected=" + expected);
                    }
            );
        } catch (Exception e) {
            violations.add("Error querying v_inventory_consistency: " + e.getMessage());
        }

        // 2. Invariant: No negative stock in inventory
        try {
            Integer negativeCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM inventory WHERE quantity < 0",
                    Integer.class
            );
            if (negativeCount != null && negativeCount > 0) {
                violations.add("CRITICAL: Found " + negativeCount + " inventory rows with negative quantity");
            }
        } catch (Exception e) {
            violations.add("Error querying negative stock: " + e.getMessage());
        }

        // 3. Invariant: Every COMPLETED order must have a corresponding inventory_movements row
        try {
            Integer unlinkedCompletedOrders = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM orders o LEFT JOIN inventory_movements m ON o.event_id = m.event_id " +
                            "WHERE o.status = 'COMPLETED' AND m.id IS NULL",
                    Integer.class
            );
            if (unlinkedCompletedOrders != null && unlinkedCompletedOrders > 0) {
                violations.add("Found " + unlinkedCompletedOrders + " COMPLETED orders missing from inventory_movements ledger");
            }
        } catch (Exception e) {
            violations.add("Error verifying completed orders ledger rows: " + e.getMessage());
        }

        // 4. Invariant: At most ONE open reorder per SKU (CREATED, ACKNOWLEDGED, SHIPPED)
        try {
            jdbcTemplate.query(
                    "SELECT sku, COUNT(*) as cnt FROM reorder_requests " +
                            "WHERE status IN ('CREATED','ACKNOWLEDGED','SHIPPED') " +
                            "GROUP BY sku HAVING COUNT(*) > 1",
                    rs -> {
                        violations.add("Multiple open reorders found for SKU " + rs.getString("sku") +
                                " (count=" + rs.getInt("cnt") + ")");
                    }
            );
        } catch (Exception e) {
            violations.add("Error verifying open reorder exclusivity: " + e.getMessage());
        }

        return ConsistencyReport.builder()
                .consistent(violations.isEmpty())
                .violations(violations)
                .inconsistencyCount(violations.size())
                .timestamp(Instant.now())
                .build();
    }
}
