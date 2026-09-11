package com.uba.mbp.audit;

/** Implemented once per service's logging infrastructure; called wherever RFP §3.5 requires an audit trail entry. */
public interface AuditLogger {
    void record(AuditEvent event);
}
