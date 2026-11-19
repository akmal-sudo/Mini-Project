package com.project.dbms.repository;

import com.project.dbms.entity.ReorderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReorderStatusHistoryRepository extends JpaRepository<ReorderStatusHistory, Long> {
    List<ReorderStatusHistory> findByReorderIdOrderByChangedAtDesc(Long reorderId);
}
