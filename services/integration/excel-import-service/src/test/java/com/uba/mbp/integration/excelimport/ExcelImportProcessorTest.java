package com.uba.mbp.integration.excelimport;

import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.integration.excelimport.fixtures.ExcelFixtures;
import com.uba.mbp.integration.excelimport.parse.ExcelWorkbookParser;
import com.uba.mbp.integration.excelimport.validate.ExcelRowValidator;
import com.uba.mbp.integration.excelimport.validate.ImportResult;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** End-to-end through real POI parsing + real validation, mocking only audit/clock (like the other adapters' scanner tests). */
class ExcelImportProcessorTest {

    private final AuditLogger auditLogger = mock(AuditLogger.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-15T00:00:00Z"), ZoneOffset.UTC);
    private final ExcelImportProcessor processor =
            new ExcelImportProcessor(new ExcelWorkbookParser(), new ExcelRowValidator(), auditLogger, clock);

    @Test
    void acceptsValidRowsAndRejectsInvalidOnesInTheSameUpload() {
        InputStream workbook = ExcelFixtures.standardWorkbook(List.of(
                List.of("ACC-001", "CUST-1", "SOL-001", "NGN", "TXN-1", "written off", "50000.00", "2026-01-15", "NG"),
                List.of("ACC-002", "CUST-2", "SOL-002", "NGN", "TXN-2", "written off", "not-a-number", "2026-01-16", "NG"),
                Arrays.asList(null, "CUST-3", "SOL-003", "NGN", "TXN-3", "written off", "1000", "2026-01-17", "NG")));

        ImportResult result = processor.process("maxim-user-1", "upload.xlsx", workbook);

        assertEquals(3, result.outcome().totalRows());
        assertEquals(1, result.outcome().accepted());
        assertEquals(2, result.outcome().rejected());
        assertEquals(1, result.acceptedEvents().size());
        assertEquals("ACC-001", result.acceptedEvents().get(0).accountNumber());

        assertTrue(result.outcome().rows().stream()
                .anyMatch(row -> row.rowNumber() == 3 && !row.accepted() && row.reason().contains("Balance")));
        assertTrue(result.outcome().rows().stream()
                .anyMatch(row -> row.rowNumber() == 4 && !row.accepted() && row.reason().contains("Account Number")));
    }

    @Test
    void auditsEveryAcceptedRowAndTheOverallUpload() {
        InputStream workbook = ExcelFixtures.standardWorkbook(List.of(
                List.of("ACC-001", "CUST-1", "SOL-001", "NGN", "TXN-1", "written off", "50000.00", "2026-01-15", "NG"),
                List.of("ACC-002", "CUST-2", "SOL-002", "NGN", "TXN-2", "written off", "60000.00", "2026-01-16", "NG")));

        processor.process("maxim-user-1", "upload.xlsx", workbook);

        // 2 accepted-row audits + 1 overall-upload audit
        verify(auditLogger, times(3)).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void producesNoAcceptedEventsWhenEveryRowIsInvalid() {
        InputStream workbook = ExcelFixtures.standardWorkbook(List.of(
                Arrays.asList(null, null, null, null, null, "written off", "1000", "2026-01-15", "NG")));

        ImportResult result = processor.process("maxim-user-1", "upload.xlsx", workbook);

        assertFalse(result.outcome().accepted() > 0);
        assertTrue(result.acceptedEvents().isEmpty());
        assertEquals(1, result.outcome().rejected());
    }
}
