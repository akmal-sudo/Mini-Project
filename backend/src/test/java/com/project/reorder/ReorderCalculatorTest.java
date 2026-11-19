package com.project.reorder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("REORDER")
class ReorderCalculatorTest {

    @Test
    @Tag("FR-17")
    @DisplayName("R5: priorityScore formula verified against predefined truth table")
    void testPriorityScoreTruthTable() {
        // Case 1: CRITICAL severity, stock 0, threshold 20, frequency 10
        // Expected: 4*100 + (1-0)*50 + 10*5 = 400 + 50 + 50 = 500.00
        BigDecimal score1 = ReorderCalculator.calculatePriorityScore("CRITICAL", 0, 20, 10);
        assertEquals(new BigDecimal("500.00"), score1);

        // Case 2: HIGH severity, stock 5, threshold 20, frequency 4
        // Expected: 3*100 + (1 - 5/20)*50 + 4*5 = 300 + 37.5 + 20 = 357.50
        BigDecimal score2 = ReorderCalculator.calculatePriorityScore("HIGH", 5, 20, 4);
        assertEquals(new BigDecimal("357.50"), score2);

        // Case 3: MEDIUM severity, stock 15, threshold 20, frequency 2
        // Expected: 2*100 + (1 - 15/20)*50 + 2*5 = 200 + 12.5 + 10 = 222.50
        BigDecimal score3 = ReorderCalculator.calculatePriorityScore("MEDIUM", 15, 20, 2);
        assertEquals(new BigDecimal("222.50"), score3);

        // Case 4: LOW severity, stock 20, threshold 20, frequency 0
        // Expected: 1*100 + (1 - 20/20)*50 + 0 = 100.00
        BigDecimal score4 = ReorderCalculator.calculatePriorityScore("LOW", 20, 20, 0);
        assertEquals(new BigDecimal("100.00"), score4);

        // Case 5: Zero threshold edge case
        BigDecimal score5 = ReorderCalculator.calculatePriorityScore("CRITICAL", 0, 0, 0);
        assertEquals(new BigDecimal("450.00"), score5);
    }
}
