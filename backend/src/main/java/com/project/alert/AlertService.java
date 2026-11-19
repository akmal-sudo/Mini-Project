package com.project.alert;

import com.project.alert.AlertDecisionEngine.AlertDecision;
import com.project.dbms.entity.Alert;
import com.project.dbms.repository.AlertRepository;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository alertRepository;
    private final AlertDecisionEngine decisionEngine;
    private final WebhookDispatcher webhookDispatcher;

    public AlertService(
            AlertRepository alertRepository,
            AlertDecisionEngine decisionEngine,
            WebhookDispatcher webhookDispatcher) {
        this.alertRepository = alertRepository;
        this.decisionEngine = decisionEngine;
        this.webhookDispatcher = webhookDispatcher;
    }

    /**
     * Evaluates incoming log event or inventory change and creates an alert if necessary.
     */
    @Transactional
    public Optional<Alert> processEventForAlerts(
            String sku,
            int quantity,
            int threshold,
            String detectedPattern,
            Instant sourceIngestedAt,
            long orderFailures,
            long restockFailures,
            long windowOrderCount,
            double baselineConsumption) {

        Optional<AlertDecision> decisionOpt = decisionEngine.evaluate(
                sku, quantity, threshold, detectedPattern,
                orderFailures, restockFailures, windowOrderCount, baselineConsumption
        );

        if (decisionOpt.isEmpty()) {
            return Optional.empty();
        }

        AlertDecision decision = decisionOpt.get();

        // Deduplication: prevent duplicate active alerts for same SKU + pattern
        Optional<Alert> existing = alertRepository.findBySkuAndPatternAndStatusIn(
                sku, decision.getPattern(), List.of("OPEN", "ACKNOWLEDGED")
        );
        if (existing.isPresent()) {
            return existing;
        }

        Alert alert = Alert.builder()
                .sku(sku)
                .pattern(decision.getPattern())
                .severity(decision.getSeverity())
                .message(decision.getMessage())
                .status("OPEN")
                .sourceIngestedAt(sourceIngestedAt != null ? sourceIngestedAt : Instant.now())
                .createdAt(Instant.now())
                .build();

        Alert saved = alertRepository.saveAndFlush(alert);
        log.info("Created Alert #{}: {} - {}", saved.getAlertId(), saved.getSeverity(), saved.getMessage());

        // Asynchronously dispatch webhook for CRITICAL or HIGH alerts
        webhookDispatcher.dispatchAlertAsync(saved);

        // Low manager stock is fixed by the manager (Replenish on the Inventory page);
        // vendors order from the manager catalog on their own, so no vendor order is auto-created here.
        if (decision.isTriggersReorder()) {
            log.info("Alert #{} needs manager replenishment of SKU {}", saved.getAlertId(), sku);
        }

        return Optional.of(saved);
    }

    @Transactional
    public Alert acknowledgeAlert(Long alertId, Integer userId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + alertId));

        alert.setStatus("ACKNOWLEDGED");
        alert.setAcknowledgedBy(userId);
        alert.setAcknowledgedAt(Instant.now());
        return alertRepository.saveAndFlush(alert);
    }

    @Transactional
    public Alert resolveAlert(Long alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + alertId));

        alert.setStatus("RESOLVED");
        alert.setResolvedAt(Instant.now());
        return alertRepository.saveAndFlush(alert);
    }

    @Getter
    @Builder
    public static class LatencyStats {
        private final double p50Ms;
        private final double p95Ms;
        private final long sampleCount;
    }

    public LatencyStats getDetectionLatencyStats() {
        List<Alert> alerts = alertRepository.findAll();
        List<Long> latencies = new ArrayList<>();

        for (Alert a : alerts) {
            if (a.getSourceIngestedAt() != null && a.getCreatedAt() != null) {
                long ms = Math.max(0, Duration.between(a.getSourceIngestedAt(), a.getCreatedAt()).toMillis());
                latencies.add(ms);
            }
        }

        if (latencies.isEmpty()) {
            return LatencyStats.builder().p50Ms(0.0).p95Ms(0.0).sampleCount(0).build();
        }

        Collections.sort(latencies);
        int p50Idx = (int) Math.floor(latencies.size() * 0.50);
        int p95Idx = (int) Math.min(latencies.size() - 1, Math.floor(latencies.size() * 0.95));

        return LatencyStats.builder()
                .p50Ms(latencies.get(p50Idx).doubleValue())
                .p95Ms(latencies.get(p95Idx).doubleValue())
                .sampleCount(latencies.size())
                .build();
    }
}
