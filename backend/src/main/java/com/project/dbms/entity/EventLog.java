package com.project.dbms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "event_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(name = "event_type", length = 20)
    private String eventType;

    @Column(length = 30)
    private String sku;

    @Column(nullable = false, length = 10)
    private String level; // INFO, WARN, ERROR

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "ingested_at", nullable = false)
    @Builder.Default
    private Instant ingestedAt = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
