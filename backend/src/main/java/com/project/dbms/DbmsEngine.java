package com.project.dbms;

import com.project.dbms.service.ConsistencyCheckService.ConsistencyReport;

public interface DbmsEngine {

    DbmsMetricsSnapshot getMetricsSnapshot();

    ConsistencyReport checkConsistency();
}
