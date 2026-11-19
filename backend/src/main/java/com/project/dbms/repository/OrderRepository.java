package com.project.dbms.repository;

import com.project.dbms.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByEventId(String eventId);
    Page<Order> findByStatus(String status, Pageable pageable);
    List<Order> findByStatusOrderByCreatedAtDesc(String status);
    List<Order> findBySkuOrderByCreatedAtDesc(String sku);
    long countByStatus(String status);
}
