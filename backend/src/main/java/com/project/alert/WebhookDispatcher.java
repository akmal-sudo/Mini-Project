package com.project.alert;

import com.project.dbms.entity.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Asynchronous HMAC-signed Webhook Dispatcher.
 * Forwards CRITICAL and HIGH severity alerts to an external webhook endpoint.
 */
@Component
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

    private final String webhookUrl;
    private final String webhookSecret;
    private final HttpClient httpClient;

    public WebhookDispatcher(
            @Value("${app.alert.webhook-url:}") String webhookUrl,
            @Value("${app.alert.webhook-secret:qc-alert-webhook-hmac-secret-key}") String webhookSecret) {
        this.webhookUrl = webhookUrl;
        this.webhookSecret = webhookSecret;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Async
    public void dispatchAlertAsync(Alert alert) {
        if (alert == null || webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }

        // Only dispatch CRITICAL and HIGH alerts
        if (!"CRITICAL".equalsIgnoreCase(alert.getSeverity()) && !"HIGH".equalsIgnoreCase(alert.getSeverity())) {
            return;
        }

        try {
            String jsonPayload = String.format(
                    "{\"alertId\":%d,\"sku\":\"%s\",\"pattern\":\"%s\",\"severity\":\"%s\",\"message\":\"%s\",\"createdAt\":\"%s\"}",
                    alert.getAlertId(),
                    escapeJson(alert.getSku()),
                    escapeJson(alert.getPattern()),
                    escapeJson(alert.getSeverity()),
                    escapeJson(alert.getMessage()),
                    alert.getCreatedAt().toString()
            );

            String signature = calculateHmacSha256(jsonPayload, webhookSecret);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("X-QC-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenAccept(res -> log.debug("Webhook dispatched for alert {}: status {}", alert.getAlertId(), res.statusCode()))
                    .exceptionally(ex -> {
                        log.warn("Webhook dispatch failed for alert {}: {}", alert.getAlertId(), ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            log.warn("Error creating webhook dispatch for alert {}: {}", alert.getAlertId(), e.getMessage());
        }
    }

    public static String calculateHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate HMAC SHA-256", e);
        }
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }
}
