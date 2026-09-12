package com.uba.mbp.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Default {@link AuditLogger}: serializes each {@link AuditEvent} as a single JSON log line.
 * Every service ships this line to the central ELK stack (ADR-0005) without needing its own
 * log shape. Never throws — a broken audit write must not break the caller's real work.
 */
@Component
public class Slf4jAuditLogger implements AuditLogger {

    private static final Logger log = LoggerFactory.getLogger("AUDIT");

    private final ObjectMapper objectMapper;

    public Slf4jAuditLogger(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void record(AuditEvent event) {
        try {
            log.info(objectMapper.writeValueAsString(event));
        } catch (JacksonException e) {
            log.warn("Failed to serialize AuditEvent as JSON, logging raw toString instead: {}", event, e);
        }
    }
}
