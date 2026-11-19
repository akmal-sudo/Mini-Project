package com.project.web.security;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory Token Bucket Rate Limiter.
 * Capacity: 100 requests.
 * Refill: 100 tokens every 60 seconds (1.67 tokens/sec).
 */
@Component
public class TokenBucketRateLimiter {

    private static final long CAPACITY = 100;
    private static final long REFILL_DURATION_MS = 60_000; // 1 minute

    private static class Bucket {
        long tokens = CAPACITY;
        long lastRefillTime = System.currentTimeMillis();

        synchronized boolean tryConsume() {
            refill();
            if (tokens > 0) {
                tokens--;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTime;
            if (elapsed > 0) {
                double tokensToAdd = ((double) elapsed / REFILL_DURATION_MS) * CAPACITY;
                if (tokensToAdd >= 1.0) {
                    tokens = Math.min(CAPACITY, tokens + (long) tokensToAdd);
                    lastRefillTime = now;
                }
            }
        }
    }

    private final ConcurrentHashMap<String, Bucket> clientBuckets = new ConcurrentHashMap<>();

    public boolean tryAcquire(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = "UNKNOWN";
        }
        Bucket bucket = clientBuckets.computeIfAbsent(clientIp, k -> new Bucket());
        return bucket.tryConsume();
    }

    public void reset() {
        clientBuckets.clear();
    }
}
