package com.flatmeals.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data writes the SQL for us from the method names.
 * JpaRepository already gives save(), findById(), findAll(), count() ...
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityTypeAndEntityIdOrderByIdDesc(String entityType, Long entityId);
}
