package com.project.reorder;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates replenishment priority scores.
 * Formula:
 * priorityScore = (severityWeight * 100) + (urgency * 50) + (frequencyInWindow * 5)
 *
 * where:
 *   severityWeight: CRITICAL=4, HIGH=3, MEDIUM=2, LOW=1
 *   urgency = (threshold == 0 ? 1 : max(0, 1 - (qty / threshold)))
 */
public class ReorderCalculator {

    public static BigDecimal calculatePriorityScore(String severity, int quantity, int threshold, long frequencyInWindow) {
        int severityWeight = switch (severity != null ? severity.toUpperCase() : "MEDIUM") {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 2;
        };

        double urgency;
        if (threshold <= 0) {
            urgency = (quantity <= 0) ? 1.0 : 0.0;
        } else {
            urgency = Math.max(0.0, 1.0 - ((double) quantity / threshold));
        }

        double score = (severityWeight * 100.0) + (urgency * 50.0) + (frequencyInWindow * 5.0);

        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }
}
