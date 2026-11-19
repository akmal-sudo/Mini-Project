package com.project.os;

import com.project.dbms.entity.FailedEvent;
import com.project.dbms.repository.FailedEventRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation of the OS Concurrency Engine.
 * Manages producer-consumer event queue, bounded backpressure, resizable worker pool,
 * and live concurrency metrics telemetry.
 */
@Service
public class OsEngineImpl implements OsEngine {

    private static final Logger log = LoggerFactory.getLogger(OsEngineImpl.class);

    private final int queueCapacity;
    private final int defaultWorkerPoolSize;
    private final long backpressureTimeoutMs;
    private volatile ConcurrencyMode concurrencyMode;
    private volatile long artificialDelayMs;

    private final BlockingQueue<StockEvent> queue;
    private final ThreadPoolExecutor workerPool;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicInteger targetPoolSize = new AtomicInteger(8);
    private final AtomicInteger activeWorkers = new AtomicInteger(0);

    private final AtomicLong producedCount = new AtomicLong(0);
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private final AtomicLong rejectedOverflowCount = new AtomicLong(0);
    private final ConcurrentHashMap<String, AtomicLong> perThreadProcessedCounts = new ConcurrentHashMap<>();

    private final InventorySyncExecutor syncExecutor;
    private final SkuLockManager skuLockManager;
    private final FailedEventRepository failedEventRepository;

    public OsEngineImpl(
            @Value("${app.os.queue-capacity:5000}") int queueCapacity,
            @Value("${app.os.worker-pool-size:8}") int workerPoolSize,
            @Value("${app.os.backpressure-timeout-ms:200}") long backpressureTimeoutMs,
            @Value("${app.os.inventory-sync-mode:LOCK}") String initialMode,
            @Value("${app.os.artificial-delay-ms:0}") long artificialDelayMs,
            InventorySyncExecutor syncExecutor,
            SkuLockManager skuLockManager,
            FailedEventRepository failedEventRepository) {

        this.queueCapacity = queueCapacity;
        this.defaultWorkerPoolSize = workerPoolSize;
        this.backpressureTimeoutMs = backpressureTimeoutMs;
        this.concurrencyMode = ConcurrencyMode.valueOf(initialMode.toUpperCase());
        this.artificialDelayMs = artificialDelayMs;
        this.syncExecutor = syncExecutor;
        this.skuLockManager = skuLockManager;
        this.failedEventRepository = failedEventRepository;

        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.targetPoolSize.set(workerPoolSize);

        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "os-worker-" + counter.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };

        this.workerPool = new ThreadPoolExecutor(
                workerPoolSize,
                50, // max allowed burst
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                threadFactory
        );
    }

    @PostConstruct
    public void init() {
        log.info("Initializing OS Engine: queueCapacity={}, workerPoolSize={}, mode={}, backpressureTimeoutMs={}",
                queueCapacity, targetPoolSize.get(), concurrencyMode, backpressureTimeoutMs);

        for (int i = 0; i < targetPoolSize.get(); i++) {
            spawnWorker();
        }
    }

    private void spawnWorker() {
        workerPool.execute(() -> {
            String threadName = Thread.currentThread().getName();
            perThreadProcessedCounts.computeIfAbsent(threadName, k -> new AtomicLong(0));

            while (running.get()) {
                try {
                    StockEvent event = queue.poll(500, TimeUnit.MILLISECONDS);
                    if (event == null) {
                        continue;
                    }
                    if (event.isPoisonPill()) {
                        log.debug("Worker {} received poison pill, shutting down", threadName);
                        break;
                    }

                    activeWorkers.incrementAndGet();
                    try {
                        ExecutionResult result = syncExecutor.execute(event, concurrencyMode, artificialDelayMs);
                        processedCount.incrementAndGet();
                        perThreadProcessedCounts.get(threadName).incrementAndGet();

                        if (!result.isSuccess() && "FAILED".equals(result.getStatus())) {
                            failedCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // One failing event never stops other workers
                        failedCount.incrementAndGet();
                        log.error("Error processing event {} on worker {}: {}", event.getEventId(), threadName, e.getMessage());
                    } finally {
                        activeWorkers.decrementAndGet();
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Unexpected worker exception on {}: {}", threadName, e.getMessage(), e);
                }
            }
        });
    }

    @Override
    public boolean submitEvent(StockEvent event) {
        if (event == null) return false;

        try {
            boolean accepted = queue.offer(event, backpressureTimeoutMs, TimeUnit.MILLISECONDS);
            if (accepted) {
                producedCount.incrementAndGet();
                return true;
            } else {
                // Backpressure rejection: write to failed_events and count overflow
                rejectedOverflowCount.incrementAndGet();
                log.warn("Backpressure rejection: Queue is full (size={}). Event {} rejected.", queue.size(), event.getEventId());

                try {
                    failedEventRepository.save(FailedEvent.builder()
                            .eventId(event.getEventId())
                            .payload(String.format("{\"sku\":\"%s\",\"type\":\"%s\",\"qty\":%d}",
                                    event.getSku(), event.getEventType(), event.getQuantity()))
                            .error("Queue overflow backpressure rejected (timeout " + backpressureTimeoutMs + "ms)")
                            .attempts(1)
                            .createdAt(Instant.now())
                            .build());
                } catch (Exception ex) {
                    log.error("Failed to record overflow event in DB: {}", ex.getMessage());
                }
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            rejectedOverflowCount.incrementAndGet();
            return false;
        }
    }

    @Override
    public ExecutionResult processStockEventSync(StockEvent event) {
        producedCount.incrementAndGet();
        try {
            ExecutionResult result = syncExecutor.execute(event, concurrencyMode, artificialDelayMs);
            processedCount.incrementAndGet();
            String threadName = Thread.currentThread().getName();
            perThreadProcessedCounts.computeIfAbsent(threadName, k -> new AtomicLong(0)).incrementAndGet();

            if (!result.isSuccess() && "FAILED".equals(result.getStatus())) {
                failedCount.incrementAndGet();
            }
            return result;
        } catch (Exception e) {
            failedCount.incrementAndGet();
            log.error("Sync execution error for event {}: {}", event.getEventId(), e.getMessage());
            return ExecutionResult.builder()
                    .success(false)
                    .eventId(event.getEventId())
                    .sku(event.getSku())
                    .eventType(event.getEventType())
                    .previousQuantity(-1)
                    .newQuantity(-1)
                    .status("FAILED")
                    .message(e.getMessage())
                    .build();
        }
    }

    @Override
    public synchronized void setWorkerPoolSize(int newSize) {
        if (newSize < 1 || newSize > 64) {
            throw new IllegalArgumentException("Worker pool size must be between 1 and 64, requested: " + newSize);
        }

        int current = targetPoolSize.get();
        targetPoolSize.set(newSize);

        if (newSize > current) {
            // Spawn additional workers
            for (int i = 0; i < (newSize - current); i++) {
                spawnWorker();
            }
        } else if (newSize < current) {
            // Send poison pills to gracefully terminate excess workers
            for (int i = 0; i < (current - newSize); i++) {
                queue.offer(StockEvent.poisonPill());
            }
        }

        log.info("Worker pool size updated from {} to {}", current, newSize);
    }

    @Override
    public int getWorkerPoolSize() {
        return targetPoolSize.get();
    }

    @Override
    public void setConcurrencyMode(ConcurrencyMode mode) {
        if (mode != null) {
            log.info("Changing inventory concurrency mode from {} to {}", this.concurrencyMode, mode);
            this.concurrencyMode = mode;
        }
    }

    @Override
    public ConcurrencyMode getConcurrencyMode() {
        return this.concurrencyMode;
    }

    @Override
    public void setArtificialDelayMs(long delayMs) {
        this.artificialDelayMs = Math.max(0, delayMs);
    }

    @Override
    public long getArtificialDelayMs() {
        return this.artificialDelayMs;
    }

    @Override
    public OsMetricsSnapshot getMetricsSnapshot() {
        Map<String, Long> threadCounts = new ConcurrentHashMap<>();
        perThreadProcessedCounts.forEach((k, v) -> threadCounts.put(k, v.get()));

        return OsMetricsSnapshot.builder()
                .queueDepth(queue.size())
                .queueCapacity(queueCapacity)
                .producedCount(producedCount.get())
                .processedCount(processedCount.get())
                .failedCount(failedCount.get())
                .rejectedOverflowCount(rejectedOverflowCount.get())
                .activeWorkerCount(activeWorkers.get())
                .workerPoolSize(targetPoolSize.get())
                .perThreadProcessedCounts(threadCounts)
                .lockContentionCount(skuLockManager.getContentionCount())
                .avgLockWaitTimeMs(skuLockManager.getAvgLockWaitTimeMs())
                .p95LockWaitTimeMs(skuLockManager.getP95LockWaitTimeMs())
                .concurrencyMode(concurrencyMode)
                .artificialDelayMs(artificialDelayMs)
                .build();
    }

    @Override
    public void resetMetrics() {
        producedCount.set(0);
        processedCount.set(0);
        failedCount.set(0);
        rejectedOverflowCount.set(0);
        perThreadProcessedCounts.clear();
        skuLockManager.resetMetrics();
    }

    @PreDestroy
    @Override
    public void shutdown() {
        log.info("Shutting down OS Engine...");
        running.set(false);

        // Send poison pills
        for (int i = 0; i < targetPoolSize.get() * 2; i++) {
            queue.offer(StockEvent.poisonPill());
        }

        workerPool.shutdown();
        try {
            if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
