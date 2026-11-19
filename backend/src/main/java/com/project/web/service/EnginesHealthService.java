package com.project.web.service;

import com.project.dbms.DbmsEngine;
import com.project.dbms.DbmsMetricsSnapshot;
import com.project.dsa.DsaEngine;
import com.project.dsa.DsaMetricsSnapshot;
import com.project.os.OsEngine;
import com.project.os.OsMetricsSnapshot;
import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Aggregates health snapshots across all four engines:
 * 1. OS Engine (concurrency, queue, worker pool, contention)
 * 2. DBMS Engine (Postgres schema, views, triggers, pool)
 * 3. DSA Engine (cache hit-ratio, KMP, priority heap, sliding window)
 * 4. Web Engine (stateless JWT, sessions, rate limiter, JVM metrics)
 */
@Service
public class EnginesHealthService {

    private final OsEngine osEngine;
    private final DbmsEngine dbmsEngine;
    private final DsaEngine dsaEngine;

    public EnginesHealthService(OsEngine osEngine, DbmsEngine dbmsEngine, DsaEngine dsaEngine) {
        this.osEngine = osEngine;
        this.dbmsEngine = dbmsEngine;
        this.dsaEngine = dsaEngine;
    }

    @Getter
    @Builder
    public static class CombinedEnginesHealth {
        private final Instant timestamp;
        private final OsMetricsSnapshot osEngine;
        private final DbmsMetricsSnapshot dbmsEngine;
        private final DsaMetricsSnapshot dsaEngine;
        private final Map<String, Object> webEngine;
    }

    public CombinedEnginesHealth getCombinedHealth() {
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        Map<String, Object> webMetrics = new HashMap<>();
        webMetrics.put("jvmUptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());
        webMetrics.put("heapMemoryUsedBytes", memBean.getHeapMemoryUsage().getUsed());
        webMetrics.put("heapMemoryMaxBytes", memBean.getHeapMemoryUsage().getMax());
        webMetrics.put("jvmThreadCount", threadBean.getThreadCount());
        webMetrics.put("statelessSessions", true);
        webMetrics.put("status", "UP");

        return CombinedEnginesHealth.builder()
                .timestamp(Instant.now())
                .osEngine(osEngine.getMetricsSnapshot())
                .dbmsEngine(dbmsEngine.getMetricsSnapshot())
                .dsaEngine(dsaEngine.getMetricsSnapshot())
                .webEngine(webMetrics)
                .build();
    }
}
