package com.project.dbms.repository;

import com.project.dbms.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    Optional<Alert> findBySkuAndPatternAndStatusIn(String sku, String pattern, Collection<String> statuses);
    List<Alert> findByStatusOrderByCreatedAtDesc(String status);
    Page<Alert> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
