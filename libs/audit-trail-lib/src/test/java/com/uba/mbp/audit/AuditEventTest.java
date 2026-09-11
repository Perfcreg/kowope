package com.uba.mbp.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuditEventTest {

    @Test
    void carriesEveryFieldTheContractRequires() {
        var event = new AuditEvent(
                Instant.parse("2026-09-11T10:00:00Z"),
                "credit-admin-1",
                "MEMO_BALANCE_ADJUSTED",
                "MemoAccount",
                "ACC-123",
                "memo-balance",
                "Adjusted for partial payment");

        assertEquals("credit-admin-1", event.actor());
        assertEquals("MemoAccount", event.affectedRecordType());
    }
}
