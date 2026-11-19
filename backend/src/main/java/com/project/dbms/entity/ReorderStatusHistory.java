package com.project.dbms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "reorder_status_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReorderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reorder_id", nullable = false)
    private Long reorderId;

    @Column(name = "old_status", length = 15)
    private String oldStatus;

    @Column(name = "new_status", nullable = false, length = 15)
    private String newStatus;

    @Column(name = "changed_by")
    private Integer changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant changedAt = Instant.now();
}
