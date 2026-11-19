package com.project.os;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * In-memory representation of an event in the OS processing pipeline.
 */
@Getter
@Builder
@ToString
public class StockEvent {

    private final String eventId;
    private final String eventType; // ORDER, CANCEL, RESTOCK, WAREHOUSE_SCAN, RESTOCK_FAILED
    private final String sku;
    private final int quantity;
    private final Instant timestamp;
    private final boolean poisonPill;

    public static StockEvent order(String sku, int quantity) {
        return StockEvent.builder()
                .eventId("EVT-" + UUID.randomUUID())
                .eventType("ORDER")
                .sku(sku)
                .quantity(quantity)
                .timestamp(Instant.now())
                .poisonPill(false)
                .build();
    }

    public static StockEvent cancel(String sku, int quantity) {
        return StockEvent.builder()
                .eventId("EVT-" + UUID.randomUUID())
                .eventType("CANCEL")
                .sku(sku)
                .quantity(quantity)
                .timestamp(Instant.now())
                .poisonPill(false)
                .build();
    }

    public static StockEvent restock(String sku, int quantity) {
        return StockEvent.builder()
                .eventId("EVT-" + UUID.randomUUID())
                .eventType("RESTOCK")
                .sku(sku)
                .quantity(quantity)
                .timestamp(Instant.now())
                .poisonPill(false)
                .build();
    }

    public static StockEvent warehouseScan(String sku) {
        return StockEvent.builder()
                .eventId("EVT-" + UUID.randomUUID())
                .eventType("WAREHOUSE_SCAN")
                .sku(sku)
                .quantity(0)
                .timestamp(Instant.now())
                .poisonPill(false)
                .build();
    }

    public static StockEvent restockFailed(String sku) {
        return StockEvent.builder()
                .eventId("EVT-" + UUID.randomUUID())
                .eventType("RESTOCK_FAILED")
                .sku(sku)
                .quantity(0)
                .timestamp(Instant.now())
                .poisonPill(false)
                .build();
    }

    public static StockEvent poisonPill() {
        return StockEvent.builder()
                .eventId("POISON-PILL")
                .eventType("SHUTDOWN")
                .sku("SYSTEM")
                .quantity(0)
                .timestamp(Instant.now())
                .poisonPill(true)
                .build();
    }
}
