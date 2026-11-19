package com.project.dbms.repository;

import com.project.dbms.entity.ReorderRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReorderRequestRepository extends JpaRepository<ReorderRequest, Long> {
    Optional<ReorderRequest> findByVendorIdAndSkuAndStatusIn(Integer vendorId, String sku, Collection<String> statuses);
    List<ReorderRequest> findByVendorIdOrderByCreatedAtDesc(Integer vendorId);
    List<ReorderRequest> findByStatusOrderByCreatedAtDesc(String status);
    Page<ReorderRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
    long countByStatus(String status);
}
