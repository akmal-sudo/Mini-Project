package com.project.dbms.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "log_patterns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogPattern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 30)
    private String name;

    @Column(name = "pattern_text", nullable = false, length = 100)
    private String patternText;

    @Column(name = "severity_hint", length = 10)
    private String severityHint; // LOW, MEDIUM, HIGH, CRITICAL

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;
}
