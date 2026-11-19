package com.project.dbms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One product in a vendor's OWN catalog (vendor DB / schema "vendor").
 * quantity is the vendor's stock count. source = OWN for the vendor's original
 * range, MANAGER_CATALOG for items the vendor added by ordering from the manager.
 */
@Entity
@Table(name = "vendor_catalog", schema = "vendor")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorCatalogItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vendor_id", nullable = false)
    private Integer vendorId;

    @Column(nullable = false, length = 30)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 80)
    private String category;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String source = "OWN";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
