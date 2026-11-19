package com.project.dbms.repository;

import com.project.dbms.entity.VendorCatalogItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VendorCatalogRepository extends JpaRepository<VendorCatalogItem, Long> {

    List<VendorCatalogItem> findByVendorIdOrderBySkuAsc(Integer vendorId);

    Optional<VendorCatalogItem> findByVendorIdAndSku(Integer vendorId, String sku);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM VendorCatalogItem c WHERE c.vendorId = :vendorId AND c.sku = :sku")
    Optional<VendorCatalogItem> findForUpdate(@Param("vendorId") Integer vendorId, @Param("sku") String sku);
}
