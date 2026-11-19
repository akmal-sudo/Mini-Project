package com.project.dbms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "reorder_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReorderRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reorder_id")
    private Long reorderId;

    @Column(nullable = false, length = 30)
    private String sku;

    @Column(name = "vendor_id", nullable = false)
    private Integer vendorId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "priority_score", nullable = false, precision = 8, scale = 2)
    private BigDecimal priorityScore;

    @Column(length = 10)
    private String severity;

    @Column(nullable = false, length = 15)
    @Builder.Default
    private String status = "CREATED"; // CREATED, ACKNOWLEDGED, SHIPPED, DELIVERED, CANCELLED

    @Column(name = "alert_id")
    private Long alertId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Column(name = "delivered_at")
    private Instant deliveredAt;
}
