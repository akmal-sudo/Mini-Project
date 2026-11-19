package com.project.dbms;

import com.project.dbms.service.ConsistencyCheckService;
import com.project.dbms.service.ConsistencyCheckService.ConsistencyReport;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.Instant;

/**
 * Concrete implementation of the DBMS Engine.
 * Monitored database transactions, HikariCP connection health,
 * and ledger consistency verification.
 */
@Service
public class DbmsEngineImpl implements DbmsEngine {

    private final ConsistencyCheckService consistencyCheckService;
    private final DataSource dataSource;

    public DbmsEngineImpl(ConsistencyCheckService consistencyCheckService, DataSource dataSource) {
        this.consistencyCheckService = consistencyCheckService;
        this.dataSource = dataSource;
    }

    @Override
    public ConsistencyReport checkConsistency() {
        return consistencyCheckService.runConsistencyCheck();
    }

    @Override
    public DbmsMetricsSnapshot getMetricsSnapshot() {
        int active = 0;
        int idle = 0;
        int total = 0;
        int maxPool = 30;

        if (dataSource instanceof HikariDataSource hikari) {
            var pool = hikari.getHikariPoolMXBean();
            if (pool != null) {
                active = pool.getActiveConnections();
                idle = pool.getIdleConnections();
                total = pool.getTotalConnections();
            }
            maxPool = hikari.getMaximumPoolSize();
        }

        ConsistencyReport report = consistencyCheckService.runConsistencyCheck();

        return DbmsMetricsSnapshot.builder()
                .activeConnections(active)
                .idleConnections(idle)
                .totalConnections(total)
                .maxPoolSize(maxPool)
                .ledgerConsistent(report.isConsistent())
                .inconsistencyViolationsCount(report.getInconsistencyCount())
                .timestamp(Instant.now())
                .build();
    }
}
