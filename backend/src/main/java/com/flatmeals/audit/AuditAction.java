package com.flatmeals.audit;

/** Matches the audit_log.action ENUM column. */
public enum AuditAction {
    CREATE, UPDATE, VOID, CLOSE, REOPEN
}
