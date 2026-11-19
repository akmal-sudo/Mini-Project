package com.project.dbms.repository;

import com.project.dbms.entity.LogPattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LogPatternRepository extends JpaRepository<LogPattern, Integer> {
    List<LogPattern> findByEnabledTrue();
    Optional<LogPattern> findByName(String name);
}
