package com.flatmeals.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/**
 * Records important actions in the audit_log table.
 *
 * Services call this inside their own @Transactional method, so the change
 * and its audit row are saved together, or not at all.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * @param actorUserId who did it (null for the system itself)
     * @param entityType  e.g. "MEMBER", "USER", "EXPENSE"
     * @param entityId    id of the changed record
     * @param oldValue    any object (a Map works well); stored as JSON. null for creates.
     * @param newValue    any object; stored as JSON. NEVER pass passwords or hashes.
     * @param reason      optional explanation
     */
    public void record(Long actorUserId, String entityType, Long entityId, AuditAction action,
                       Object oldValue, Object newValue, String reason) {
        auditLogRepository.save(new AuditLog(actorUserId, entityType, entityId, action,
                toJson(oldValue), toJson(newValue), reason));
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not write audit value as JSON", e);
        }
    }
}
