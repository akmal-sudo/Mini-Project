package com.project.web.websocket;

import com.project.os.OsEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Publishes domain events and scheduled live metrics over STOMP WebSocket topics:
 * - /topic/inventory-updates
 * - /topic/alerts
 * - /topic/reorders
 * - /topic/logs
 * - /topic/os-metrics (emitted 1/sec)
 */
@Component
@EnableScheduling
public class WebSocketPublisher {

    private static final Logger log = LoggerFactory.getLogger(WebSocketPublisher.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final OsEngine osEngine;

    public WebSocketPublisher(SimpMessagingTemplate messagingTemplate, OsEngine osEngine) {
        this.messagingTemplate = messagingTemplate;
        this.osEngine = osEngine;
    }

    public void publishInventoryUpdate(Object data) {
        sendSafe("/topic/inventory-updates", data);
    }

    public void publishAlert(Object alert) {
        sendSafe("/topic/alerts", alert);
    }

    public void publishReorder(Object reorder) {
        sendSafe("/topic/reorders", reorder);
    }

    public void publishLog(Object logEvent) {
        sendSafe("/topic/logs", logEvent);
    }

    @Scheduled(fixedRate = 1000)
    public void publishLiveMetrics() {
        try {
            sendSafe("/topic/os-metrics", osEngine.getMetricsSnapshot());
        } catch (Exception e) {
            log.trace("STOMP metrics heartbeat failed: {}", e.getMessage());
        }
    }

    private void sendSafe(String destination, Object payload) {
        try {
            messagingTemplate.convertAndSend(destination, payload);
        } catch (Exception e) {
            log.debug("WebSocket broadcast error to {}: {}", destination, e.getMessage());
        }
    }
}
