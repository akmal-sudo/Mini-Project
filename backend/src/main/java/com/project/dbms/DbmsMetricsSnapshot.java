package com.project.dbms;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class DbmsMetricsSnapshot {
    private final int activeConnections;
    private final int idleConnections;
    private final int totalConnections;
    private final int maxPoolSize;
    private final boolean ledgerConsistent;
    private final int inconsistencyViolationsCount;
    private final Instant timestamp;
}
