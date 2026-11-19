package com.project.dbms.repository;

import com.project.dbms.entity.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {
    List<InventoryMovement> findBySkuOrderByCreatedAtDesc(String sku);
    List<InventoryMovement> findTop50ByOrderByCreatedAtDesc();
}
