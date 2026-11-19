package com.project.alert;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ALERT")
class AlertDecisionEngineTest {

    private AlertDecisionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new AlertDecisionEngine(1.2, 0.5, 3, 3, 2.5);
    }

    @Test
    @DisplayName("Rule 1: Quantity is 0 triggers CRITICAL STOCK_OUT with reorder")
    void testRule1StockOutZeroQuantity() {
        Optional<AlertDecisionEngine.AlertDecision> decision = engine.evaluate(
                "SKU-1", 0, 10, null, 0, 0, 0, 0.0);

        assertTrue(decision.isPresent());
        assertEquals("STOCK_OUT", decision.get().getPattern());
        assertEquals("CRITICAL", decision.get().getSeverity());
        assertTrue(decision.get().isTriggersReorder());
    }

    @Test
    @DisplayName("Rule 1: Detected pattern STOCK_OUT triggers CRITICAL STOCK_OUT even if quantity > 0")
    void testRule1StockOutPattern() {
        Optional<AlertDecisionEngine.AlertDecision> decision = engine.evaluate(
                "SKU-1", 5, 10, "STOCK_OUT", 0, 0, 0, 0.0);

        assertTrue(decision.isPresent());
        assertEquals("STOCK_OUT", decision.get().getPattern());
        assertEquals("CRITICAL", decision.get().getSeverity());
        assertTrue(decision.get().isTriggersReorder());
    }

    @Test
    @DisplayName("Rule 2: LOW_STOCK with quantity <= threshold * 0.5 triggers HIGH severity")
    void testRule2LowStockCritical() {
        // threshold = 20, criticalLowFactor = 0.5 -> critical <= 10. Qty = 10 -> HIGH
        Optional<AlertDecisionEngine.AlertDecision> decision = engine.evaluate(
                "SKU-2", 10, 20, null, 0, 0, 0, 0.0);

        assertTrue(decision.isPresent());
        assertEquals("LOW_STOCK", decision.get().getPattern());
        assertEquals("HIGH", decision.get().getSeverity());
        assertTrue(decision.get().isTriggersReorder());
    }

    @Test
    @DisplayName("Rule 2: LOW_STOCK with quantity > threshold * 0.5 but <= threshold triggers MEDIUM severity")
    void testRule2LowStockMedium() {
        // threshold = 20, criticalLowFactor = 0.5. Qty = 15 -> MEDIUM
        Optional<AlertDecisionEngine.AlertDecision> decision = engine.evaluate(
                "SKU-2", 15, 20, null, 0, 0, 0, 0.0);

        assertTrue(decision.isPresent());
        assertEquals("LOW_STOCK", decision.get().getPattern());
        assertEquals("MEDIUM", decision.get().getSeverity());
        assertTrue(decision.get().isTriggersReorder());
    }

    @Test
    @DisplayName("Rule 3: Early Warning triggers LOW alert without automatic reorder")
    void testRule3EarlyWarning() {
        // threshold = 20, earlyWarningFactor = 1.2 -> early warning up to 24. Qty = 22 -> LOW
        Optional<AlertDecisionEngine.AlertDecision> decision = engine.evaluate(
                "SKU-3", 22, 20, null, 0, 0, 0, 0.0);

        assertTrue(decision.isPresent());
        assertEquals("EARLY_WARNING", decision.get().getPattern());
        assertEquals("LOW", decision.get().getSeverity());
        assertFalse(decision.get().isTriggersReorder());
    }

    @Test
    @DisplayName("Rule 4: ORDER_FAILED pattern triggers MEDIUM or HIGH depending on window failure count")
    void testRule4OrderFailed() {
        // Less than threshold (2 failures < 3) -> MEDIUM
        Optional<AlertDecisionEngine.AlertDecision> medDecision = engine.evaluate(
                "SKU-4", 50, 20, "ORDER_FAILED", 2, 0, 0, 0.0);
        assertTrue(medDecision.isPresent());
        assertEquals("ORDER_FAILED", medDecision.get().getPattern());
        assertEquals("MEDIUM", medDecision.get().getSeverity());
        assertFalse(medDecision.get().isTriggersReorder());

        // At or above threshold (3 failures >= 3) -> HIGH
        Optional<AlertDecisionEngine.AlertDecision> highDecision = engine.evaluate(
                "SKU-4", 50, 20, "ORDER_FAILED", 3, 0, 0, 0.0);
        assertTrue(highDecision.isPresent());
        assertEquals("ORDER_FAILED", highDecision.get().getPattern());
        assertEquals("HIGH", highDecision.get().getSeverity());
    }

    @Test
    @DisplayName("Rule 5: RESTOCK_FAILED triggers MEDIUM or HIGH depending on window failure count")
    void testRule5RestockFailed() {
        Optional<AlertDecisionEngine.AlertDecision> med = engine.evaluate(
                "SKU-5", 50, 20, "RESTOCK_FAILED", 0, 1, 0, 0.0);
        assertTrue(med.isPresent());
        assertEquals("RESTOCK_FAILED", med.get().getPattern());
        assertEquals("MEDIUM", med.get().getSeverity());

        Optional<AlertDecisionEngine.AlertDecision> high = engine.evaluate(
                "SKU-5", 50, 20, "RESTOCK_FAILED", 0, 3, 0, 0.0);
        assertTrue(high.isPresent());
        assertEquals("RESTOCK_FAILED", high.get().getPattern());
        assertEquals("HIGH", high.get().getSeverity());
    }

    @Test
    @DisplayName("Rule 6: Abnormal consumption spike triggers HIGH alert")
    void testRule6AbnormalConsumption() {
        // baseline = 2.0, abnormalFactor = 2.5 -> threshold 5.0. windowOrderCount = 6 -> HIGH
        Optional<AlertDecisionEngine.AlertDecision> spike = engine.evaluate(
                "SKU-6", 100, 20, null, 0, 0, 6, 2.0);

        assertTrue(spike.isPresent());
        assertEquals("ABNORMAL_CONSUMPTION", spike.get().getPattern());
        assertEquals("HIGH", spike.get().getSeverity());
        assertFalse(spike.get().isTriggersReorder());
    }

    @Test
    @DisplayName("Normal state returns Optional.empty()")
    void testNormalState() {
        // Qty = 50, threshold = 20, early warning 24. No patterns.
        Optional<AlertDecisionEngine.AlertDecision> normal = engine.evaluate(
                "SKU-7", 50, 20, null, 0, 0, 2, 2.0);

        assertTrue(normal.isEmpty());
    }
}
