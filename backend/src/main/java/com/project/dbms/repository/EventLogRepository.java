package com.project.dbms.repository;

import com.project.dbms.entity.EventLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventLogRepository extends JpaRepository<EventLog, Long> {
    Page<EventLog> findByLevel(String level, Pageable pageable);
    Page<EventLog> findByEventType(String eventType, Pageable pageable);
    List<EventLog> findTop100ByOrderByCreatedAtDesc();
    List<EventLog> findByLevelOrderByCreatedAtDesc(String level);
    List<EventLog> findBySkuOrderByCreatedAtDesc(String sku);
}
