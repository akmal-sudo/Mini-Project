package com.project.alert;

import lombok.Builder;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Rule-based Decision Tree evaluating inventory stock levels,
 * KMP pattern signatures, and sliding window event frequencies.
 */
@Service
public class AlertDecisionEngine {

    @Getter
    @Builder
    public static class AlertDecision {
        private final String pattern;
        private final String severity; // LOW, MEDIUM, HIGH, CRITICAL
        private final String message;
        private final boolean triggersReorder;
    }

    private final double earlyWarningFactor;
    private final double criticalLowFactor;
    private final int orderFailureThreshold;
    private final int restockFailureThreshold;
    private final double abnormalConsumptionFactor;

    public AlertDecisionEngine(
            @Value("${app.alert.early-warning-factor:1.2}") double earlyWarningFactor,
            @Value("${app.alert.critical-low-factor:0.5}") double criticalLowFactor,
            @Value("${app.alert.order-failure-threshold:3}") int orderFailureThreshold,
            @Value("${app.alert.restock-failure-threshold:3}") int restockFailureThreshold,
            @Value("${app.alert.abnormal-consumption-factor:2.5}") double abnormalConsumptionFactor) {
        this.earlyWarningFactor = earlyWarningFactor;
        this.criticalLowFactor = criticalLowFactor;
        this.orderFailureThreshold = orderFailureThreshold;
        this.restockFailureThreshold = restockFailureThreshold;
        this.abnormalConsumptionFactor = abnormalConsumptionFactor;
    }

    public Optional<AlertDecision> evaluate(
            String sku,
            int quantity,
            int threshold,
            String detectedPattern,
            long orderFailuresInWindow,
            long restockFailuresInWindow,
            long windowOrderCount,
            double baselineConsumption) {

        // Rule 1: STOCK_OUT (Quantity is 0 or pattern detected)
        if (quantity == 0 || "STOCK_OUT".equals(detectedPattern)) {
            return Optional.of(AlertDecision.builder()
                    .pattern("STOCK_OUT")
                    .severity("CRITICAL")
                    .message("CRITICAL: SKU " + sku + " is completely out of stock (qty=" + quantity + ")")
                    .triggersReorder(true)
                    .build());
        }

        // Rule 2: LOW_STOCK (quantity <= threshold)
        if (quantity <= threshold || "LOW_STOCK".equals(detectedPattern)) {
            String severity = (quantity <= threshold * criticalLowFactor) ? "HIGH" : "MEDIUM";
            return Optional.of(AlertDecision.builder()
                    .pattern("LOW_STOCK")
                    .severity(severity)
                    .message(severity + ": SKU " + sku + " stock (" + quantity + ") is at or below threshold (" + threshold + ")")
                    .triggersReorder(true)
                    .build());
        }

        // Rule 3: Early Warning (threshold < quantity <= 1.2 * threshold)
        if (quantity <= threshold * earlyWarningFactor) {
            return Optional.of(AlertDecision.builder()
                    .pattern("EARLY_WARNING")
                    .severity("LOW")
                    .message("LOW: SKU " + sku + " approaching threshold (" + quantity + " units remaining)")
                    .triggersReorder(false)
                    .build());
        }

        // Rule 4: ORDER_FAILED
        if ("ORDER_FAILED".equals(detectedPattern)) {
            String severity = (orderFailuresInWindow >= orderFailureThreshold) ? "HIGH" : "MEDIUM";
            return Optional.of(AlertDecision.builder()
                    .pattern("ORDER_FAILED")
                    .severity(severity)
                    .message(severity + ": Order failed for SKU " + sku + " (" + orderFailuresInWindow + " failures in window)")
                    .triggersReorder(false)
                    .build());
        }

        // Rule 5: RESTOCK_FAILED
        if ("RESTOCK_FAILED".equals(detectedPattern)) {
            if (restockFailuresInWindow >= restockFailureThreshold) {
                return Optional.of(AlertDecision.builder()
                        .pattern("RESTOCK_FAILED")
                        .severity("HIGH")
                        .message("HIGH: Repeated failed restock attempts (" + restockFailuresInWindow + ") for SKU " + sku)
                        .triggersReorder(false)
                        .build());
            } else {
                return Optional.of(AlertDecision.builder()
                        .pattern("RESTOCK_FAILED")
                        .severity("MEDIUM")
                        .message("MEDIUM: Restock attempt failed for SKU " + sku)
                        .triggersReorder(false)
                        .build());
            }
        }

        // Rule 6: Abnormal Consumption Spike
        if (baselineConsumption > 0 && windowOrderCount >= 5 &&
                windowOrderCount > baselineConsumption * abnormalConsumptionFactor) {
            return Optional.of(AlertDecision.builder()
                    .pattern("ABNORMAL_CONSUMPTION")
                    .severity("HIGH")
                    .message("HIGH: Abnormal consumption spike detected for SKU " + sku +
                            " (" + windowOrderCount + " orders vs baseline " + baselineConsumption + ")")
                    .triggersReorder(false)
                    .build());
        }

        return Optional.empty();
    }
}
