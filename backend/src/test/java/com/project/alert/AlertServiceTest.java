package com.project.alert;

import com.project.dbms.entity.Alert;
import com.project.dbms.repository.AlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("ALERT")
class AlertServiceTest {

    private AlertRepository alertRepository;
    private AlertDecisionEngine decisionEngine;
    private WebhookDispatcher webhookDispatcher;
    private AlertService alertService;

    @BeforeEach
    void setUp() {
        alertRepository = mock(AlertRepository.class);
        decisionEngine = mock(AlertDecisionEngine.class);
        webhookDispatcher = mock(WebhookDispatcher.class);

        alertService = new AlertService(alertRepository, decisionEngine, webhookDispatcher);
    }

    @Test
    @DisplayName("Process event creates alert and dispatches webhook (no vendor order is auto-created)")
    void testProcessEventCreatesAlert() {
        AlertDecisionEngine.AlertDecision decision = AlertDecisionEngine.AlertDecision.builder()
                .pattern("STOCK_OUT")
                .severity("CRITICAL")
                .message("CRITICAL: Out of stock")
                .triggersReorder(true)
                .build();

        when(decisionEngine.evaluate(anyString(), anyInt(), anyInt(), any(), anyLong(), anyLong(), anyLong(), anyDouble()))
                .thenReturn(Optional.of(decision));
        when(alertRepository.findBySkuAndPatternAndStatusIn(anyString(), anyString(), anyList()))
                .thenReturn(Optional.empty());

        Alert mockAlert = Alert.builder()
                .alertId(101L)
                .sku("SKU-1")
                .pattern("STOCK_OUT")
                .severity("CRITICAL")
                .message("CRITICAL: Out of stock")
                .status("OPEN")
                .sourceIngestedAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        when(alertRepository.saveAndFlush(any(Alert.class))).thenReturn(mockAlert);

        Optional<Alert> result = alertService.processEventForAlerts(
                "SKU-1", 0, 10, null, Instant.now(), 0, 0, 0, 0.0);

        assertTrue(result.isPresent());
        verify(webhookDispatcher).dispatchAlertAsync(mockAlert);
    }

    @Test
    @DisplayName("Deduplication prevents creating duplicate active alert for same SKU and pattern")
    void testDeduplication() {
        AlertDecisionEngine.AlertDecision decision = AlertDecisionEngine.AlertDecision.builder()
                .pattern("LOW_STOCK")
                .severity("HIGH")
                .message("HIGH: Low stock")
                .triggersReorder(true)
                .build();

        when(decisionEngine.evaluate(anyString(), anyInt(), anyInt(), any(), anyLong(), anyLong(), anyLong(), anyDouble()))
                .thenReturn(Optional.of(decision));

        Alert existingAlert = Alert.builder().alertId(55L).sku("SKU-2").pattern("LOW_STOCK").status("OPEN").build();
        when(alertRepository.findBySkuAndPatternAndStatusIn("SKU-2", "LOW_STOCK", List.of("OPEN", "ACKNOWLEDGED")))
                .thenReturn(Optional.of(existingAlert));

        Optional<Alert> result = alertService.processEventForAlerts(
                "SKU-2", 5, 20, null, Instant.now(), 0, 0, 0, 0.0);

        assertTrue(result.isPresent());
        assertEquals(55L, result.get().getAlertId());
        verify(alertRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Acknowledge and resolve alert transitions")
    void testAcknowledgeAndResolve() {
        Alert alert = Alert.builder().alertId(1L).status("OPEN").build();
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(alertRepository.saveAndFlush(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        Alert acked = alertService.acknowledgeAlert(1L, 99);
        assertEquals("ACKNOWLEDGED", acked.getStatus());
        assertEquals(99, acked.getAcknowledgedBy());
        assertNotNull(acked.getAcknowledgedAt());

        Alert resolved = alertService.resolveAlert(1L);
        assertEquals("RESOLVED", resolved.getStatus());
        assertNotNull(resolved.getResolvedAt());
    }

    @Test
    @DisplayName("Detection latency stats calculation p50 and p95")
    void testDetectionLatencyStats() {
        Instant now = Instant.now();
        Alert a1 = Alert.builder().sourceIngestedAt(now.minusMillis(50)).createdAt(now).build(); // 50ms
        Alert a2 = Alert.builder().sourceIngestedAt(now.minusMillis(100)).createdAt(now).build(); // 100ms
        Alert a3 = Alert.builder().sourceIngestedAt(now.minusMillis(200)).createdAt(now).build(); // 200ms

        when(alertRepository.findAll()).thenReturn(List.of(a1, a2, a3));

        AlertService.LatencyStats stats = alertService.getDetectionLatencyStats();
        assertEquals(3, stats.getSampleCount());
        assertEquals(100.0, stats.getP50Ms());
        assertEquals(200.0, stats.getP95Ms());
    }

    @Test
    @DisplayName("Detection latency stats with empty alerts or null timestamps")
    void testDetectionLatencyStatsEmpty() {
        when(alertRepository.findAll()).thenReturn(List.of(
                Alert.builder().sourceIngestedAt(null).createdAt(Instant.now()).build()
        ));
        AlertService.LatencyStats stats = alertService.getDetectionLatencyStats();
        assertEquals(0, stats.getSampleCount());
        assertEquals(0.0, stats.getP50Ms());

        when(alertRepository.findAll()).thenReturn(List.of());
        AlertService.LatencyStats stats2 = alertService.getDetectionLatencyStats();
        assertEquals(0, stats2.getSampleCount());
    }

    @Test
    @DisplayName("Alert not found throws IllegalArgumentException")
    void testAlertNotFound() {
        when(alertRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> alertService.acknowledgeAlert(999L, 1));
        assertThrows(IllegalArgumentException.class, () -> alertService.resolveAlert(999L));
    }
}
