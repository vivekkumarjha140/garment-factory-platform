package com.garmentfactory.audit.api;

/** Port other modules use to record audit events. Writes join the caller's transaction. */
public interface AuditRecorder {

    void record(AuditEvent event);
}