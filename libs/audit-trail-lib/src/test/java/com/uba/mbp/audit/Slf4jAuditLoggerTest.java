package com.uba.mbp.audit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Slf4jAuditLoggerTest {

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Slf4jAuditLogger auditLogger;

    @BeforeEach
    void attachAppender() {
        Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
        appender.start();
        auditLogger.addAppender(appender);

        ObjectMapper objectMapper = JsonMapper.builder().build();
        this.auditLogger = new Slf4jAuditLogger(objectMapper);
    }

    @AfterEach
    void detachAppender() {
        ((Logger) LoggerFactory.getLogger("AUDIT")).detachAppender(appender);
    }

    @Test
    void logsTheEventAsOneJsonLineAtInfoLevel() {
        var event = new AuditEvent(
                Instant.parse("2026-09-11T10:00:00Z"),
                "credit-admin-1",
                "MEMO_BALANCE_ADJUSTED",
                "MemoAccount",
                "ACC-123",
                "memo-balance",
                "Adjusted for partial payment");

        auditLogger.record(event);

        assertEquals(1, appender.list.size());
        ILoggingEvent logged = appender.list.get(0);
        assertEquals(Level.INFO, logged.getLevel());
        assertTrue(logged.getFormattedMessage().contains("\"actor\":\"credit-admin-1\""));
        assertTrue(logged.getFormattedMessage().contains("\"affectedRecordId\":\"ACC-123\""));
    }
}
