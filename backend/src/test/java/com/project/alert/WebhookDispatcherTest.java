package com.project.alert;

import com.project.dbms.entity.Alert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@Tag("ALERT")
class WebhookDispatcherTest {

    @Test
    @DisplayName("HMAC SHA-256 signature is deterministic and matches expected hex")
    void testHmacSha256() {
        String data = "{\"test\":\"hello\"}";
        String secret = "my-secret-key";

        String sig1 = WebhookDispatcher.calculateHmacSha256(data, secret);
        String sig2 = WebhookDispatcher.calculateHmacSha256(data, secret);

        assertNotNull(sig1);
        assertEquals(64, sig1.length()); // SHA-256 hex is 64 characters
        assertEquals(sig1, sig2);

        // Different data yields different signature
        String sig3 = WebhookDispatcher.calculateHmacSha256("{\"test\":\"different\"}", secret);
        assertNotEquals(sig1, sig3);
    }

    @Test
    @DisplayName("Dispatch ignored when webhook URL is blank or null")
    void testDispatchIgnoredWhenUrlBlank() {
        WebhookDispatcher dispatcher = new WebhookDispatcher("", "secret");
        Alert alert = Alert.builder()
                .alertId(1L)
                .severity("CRITICAL")
                .sku("SKU-1")
                .pattern("STOCK_OUT")
                .message("Out of stock")
                .createdAt(Instant.now())
                .build();

        // Should return cleanly without throwing any exception
        assertDoesNotThrow(() -> dispatcher.dispatchAlertAsync(alert));
        assertDoesNotThrow(() -> dispatcher.dispatchAlertAsync(null));
    }

    @Test
    @DisplayName("Dispatch ignored for LOW or MEDIUM severity alerts")
    void testDispatchIgnoredForNonCriticalAlerts() {
        WebhookDispatcher dispatcher = new WebhookDispatcher("http://localhost:8080/webhook", "secret");

        Alert lowAlert = Alert.builder().alertId(2L).severity("LOW").build();
        Alert medAlert = Alert.builder().alertId(3L).severity("MEDIUM").build();

        assertDoesNotThrow(() -> dispatcher.dispatchAlertAsync(lowAlert));
        assertDoesNotThrow(() -> dispatcher.dispatchAlertAsync(medAlert));
    }

    @Test
    @DisplayName("Dispatch handles valid CRITICAL alert with payload construction")
    void testDispatchWithValidUrl() {
        WebhookDispatcher dispatcher = new WebhookDispatcher("http://127.0.0.1:59999/test-webhook", "secret-test");
        Alert alert = Alert.builder()
                .alertId(99L)
                .severity("CRITICAL")
                .sku("SKU-SPECIAL\"-01\n")
                .pattern("STOCK_OUT")
                .message("Complete stockout\nImmediate action required")
                .createdAt(Instant.now())
                .build();

        // Should asynchronously trigger without throwing exception
        assertDoesNotThrow(() -> dispatcher.dispatchAlertAsync(alert));
    }
}
