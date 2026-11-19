package com.project.simulation;

import com.project.os.OsEngine;
import com.project.os.StockEvent;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Replays dataset records or generates synthetic events with preserved timestamp clustering.
 */
@Service
public class DatasetEventProducer {

    private static final Logger log = LoggerFactory.getLogger(DatasetEventProducer.class);

    private final OsEngine osEngine;
    private final ResourceLoader resourceLoader;
    private final String datasetPath;
    private final double defaultEventsPerSec;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicLong emittedCount = new AtomicLong(0);
    private final ExecutorService producerExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "dataset-producer");
        t.setDaemon(true);
        return t;
    });
    private Future<?> producerFuture;

    private volatile double currentEventsPerSec;
    private volatile Long configuredSeed;

    public DatasetEventProducer(
            OsEngine osEngine,
            ResourceLoader resourceLoader,
            @Value("${app.simulation.dataset-path:classpath:dataset/online_retail_sample.csv}") String datasetPath,
            @Value("${app.simulation.default-events-per-sec:50.0}") double defaultEventsPerSec) {
        this.osEngine = osEngine;
        this.resourceLoader = resourceLoader;
        this.datasetPath = datasetPath;
        this.defaultEventsPerSec = defaultEventsPerSec;
        this.currentEventsPerSec = defaultEventsPerSec;
    }

    public synchronized void start(Double eventsPerSec, Long seed) {
        if (running.get()) {
            log.warn("DatasetEventProducer is already running");
            return;
        }

        if (eventsPerSec != null && eventsPerSec > 0) {
            this.currentEventsPerSec = eventsPerSec;
        } else {
            this.currentEventsPerSec = defaultEventsPerSec;
        }
        this.configuredSeed = seed;

        running.set(true);
        paused.set(false);
        emittedCount.set(0);

        producerFuture = producerExecutor.submit(this::produceLoop);
        log.info("DatasetEventProducer started: rate={} events/sec, seed={}", currentEventsPerSec, configuredSeed);
    }

    public synchronized void pause() {
        paused.set(true);
        log.info("DatasetEventProducer paused");
    }

    public synchronized void resume() {
        paused.set(false);
        log.info("DatasetEventProducer resumed");
    }

    public synchronized void stop() {
        running.set(false);
        paused.set(false);
        if (producerFuture != null) {
            producerFuture.cancel(true);
        }
        log.info("DatasetEventProducer stopped. Total emitted: {}", emittedCount.get());
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean isPaused() {
        return paused.get();
    }

    public long getEmittedCount() {
        return emittedCount.get();
    }

    public double getCurrentEventsPerSec() {
        return currentEventsPerSec;
    }

    private void produceLoop() {
        List<StockEvent> events = loadDatasetEvents();
        if (events.isEmpty()) {
            log.warn("No events loaded from dataset, generating synthetic stream instead");
            events = generateSyntheticEvents(1000, configuredSeed);
        }

        int index = 0;
        while (running.get()) {
            if (paused.get()) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }

            StockEvent event = events.get(index % events.size());
            // Assign fresh UUID for each emission
            StockEvent freshEvent = StockEvent.builder()
                    .eventId("EVT-" + UUID.randomUUID())
                    .eventType(event.getEventType())
                    .sku(event.getSku())
                    .quantity(event.getQuantity())
                    .timestamp(Instant.now())
                    .poisonPill(false)
                    .build();

            osEngine.submitEvent(freshEvent);
            emittedCount.incrementAndGet();
            index++;

            // Rate throttling
            if (currentEventsPerSec > 0) {
                long sleepNanos = (long) (1_000_000_000.0 / currentEventsPerSec);
                long sleepMs = sleepNanos / 1_000_000;
                int remNanos = (int) (sleepNanos % 1_000_000);
                try {
                    Thread.sleep(sleepMs, remNanos);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private List<StockEvent> loadDatasetEvents() {
        List<StockEvent> list = new ArrayList<>();
        try {
            Resource resource = resourceLoader.getResource(datasetPath);
            if (!resource.exists()) {
                return list;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                boolean isHeader = true;
                while ((line = reader.readLine()) != null) {
                    if (isHeader) {
                        isHeader = false;
                        continue;
                    }

                    String[] parts = line.split(",");
                    if (parts.length >= 4) {
                        String invoiceNo = parts[0].trim();
                        String sku = parts[1].trim();
                        int qty = Integer.parseInt(parts[3].trim());

                        String type = invoiceNo.startsWith("C") || qty < 0 ? "CANCEL" : "ORDER";
                        list.add(StockEvent.builder()
                                .eventId("EVT-" + UUID.randomUUID())
                                .eventType(type)
                                .sku(sku)
                                .quantity(Math.max(1, Math.abs(qty)))
                                .timestamp(Instant.now())
                                .poisonPill(false)
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not load dataset file from {}: {}", datasetPath, e.getMessage());
        }
        return list;
    }

    public List<StockEvent> generateSyntheticEvents(int count, Long seed) {
        Random rand = seed != null ? new Random(seed) : new Random();
        List<StockEvent> list = new ArrayList<>(count);

        String[] sampleSkus = {
                "SKU-MILK-01", "SKU-EGGS-02", "SKU-BREAD-03", "SKU-BANANA-04", "SKU-APPLE-05",
                "SKU-CHICKEN-06", "SKU-BUTTER-07", "SKU-YOGURT-08", "SKU-CHEESE-09", "SKU-AVOCADO-10"
        };

        for (int i = 0; i < count; i++) {
            // Skewed Pareto distribution: 80% of events go to first 2 SKUs
            String sku = rand.nextDouble() < 0.80 ? sampleSkus[rand.nextInt(2)] : sampleSkus[rand.nextInt(sampleSkus.length)];
            double r = rand.nextDouble();
            String type;
            int qty;

            if (r < 0.70) {
                type = "ORDER";
                qty = rand.nextInt(3) + 1;
            } else if (r < 0.85) {
                type = "CANCEL";
                qty = rand.nextInt(2) + 1;
            } else if (r < 0.95) {
                type = "WAREHOUSE_SCAN";
                qty = 0;
            } else {
                type = "RESTOCK_FAILED";
                qty = 0;
            }

            list.add(StockEvent.builder()
                    .eventId("EVT-" + UUID.randomUUID())
                    .eventType(type)
                    .sku(sku)
                    .quantity(qty)
                    .timestamp(Instant.now())
                    .poisonPill(false)
                    .build());
        }

        return list;
    }

    @PreDestroy
    public void cleanup() {
        stop();
        producerExecutor.shutdownNow();
    }
}
