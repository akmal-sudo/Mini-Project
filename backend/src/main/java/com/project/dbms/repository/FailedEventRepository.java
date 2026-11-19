package com.project.dbms.repository;

import com.project.dbms.entity.FailedEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FailedEventRepository extends JpaRepository<FailedEvent, Long> {
    List<FailedEvent> findByStatus(String status);
    Page<FailedEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
