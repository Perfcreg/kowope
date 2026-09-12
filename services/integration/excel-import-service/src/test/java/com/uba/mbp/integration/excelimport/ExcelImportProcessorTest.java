package com.uba.mbp.integration.excelimport;

import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.integration.excelimport.fixtures.ExcelFixtures;
import com.uba.mbp.integration.excelimport.parse.ExcelWorkbookParser;
import com.uba.mbp.integration.excelimport.validate.ExcelRowValidator;
import com.uba.mbp.integration.excelimport.validate.ImportOutcome;
import com.uba.mbp.integration.excelimport.validate.ImportResult;
import com.uba.mbp.integration.excelimport.validate.RowOutcome;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
        assertEquals(1, result.acceptedRows().size());
        assertEquals(2, result.acceptedRows().get(0).rowNumber());
        assertEquals("ACC-001", result.acceptedRows().get(0).event().accountNumber());

        assertTrue(result.outcome().rows().stream()
                .anyMatch(row -> row.rowNumber() == 3 && !row.accepted() && row.reason().contains("Balance")));
        assertTrue(result.outcome().rows().stream()
                .anyMatch(row -> row.rowNumber() == 4 && !row.accepted() && row.reason().contains("Account Number")));
    }

    @Test
    void auditsEveryAcceptedRowButNotTheFileLevelSummary() {
        // The file-level summary is written separately by auditFinalOutcome(),
        // after the route knows the real (post-publish) outcome — see
        // reconcileWithPublishFailures's own test for why.
        InputStream workbook = ExcelFixtures.standardWorkbook(List.of(
                List.of("ACC-001", "CUST-1", "SOL-001", "NGN", "TXN-1", "written off", "50000.00", "2026-01-15", "NG"),
                List.of("ACC-002", "CUST-2", "SOL-002", "NGN", "TXN-2", "written off", "60000.00", "2026-01-16", "NG")));

        processor.process("maxim-user-1", "upload.xlsx", workbook);

        verify(auditLogger, times(2)).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void producesNoAcceptedEventsWhenEveryRowIsInvalid() {
        InputStream workbook = ExcelFixtures.standardWorkbook(List.of(
                Arrays.asList(null, null, null, null, null, "written off", "1000", "2026-01-15", "NG")));

        ImportResult result = processor.process("maxim-user-1", "upload.xlsx", workbook);

        assertFalse(result.outcome().accepted() > 0);
        assertTrue(result.acceptedRows().isEmpty());
        assertEquals(1, result.outcome().rejected());
    }

    @Test
    void reconciliationReturnsTheSameOutcomeWhenNothingFailedToPublish() {
        ImportOutcome outcome = new ImportOutcome("upload.xlsx", 1, 1, 0,
                List.of(new RowOutcome(2, "ACC-001", true, null)));

        ImportOutcome reconciled = processor.reconcileWithPublishFailures(outcome, Set.of());

        assertSame(outcome, reconciled);
    }

    @Test
    void reconciliationFlipsAnAcceptedRowToRejectedWhenItsPublishFailed() {
        ImportOutcome outcome = new ImportOutcome("upload.xlsx", 2, 2, 0, List.of(
                new RowOutcome(2, "ACC-001", true, null),
                new RowOutcome(3, "ACC-002", true, null)));

        ImportOutcome reconciled = processor.reconcileWithPublishFailures(outcome, Set.of(3));

        assertEquals(1, reconciled.accepted());
        assertEquals(1, reconciled.rejected());
        RowOutcome flipped = reconciled.rows().stream().filter(r -> r.rowNumber() == 3).findFirst().orElseThrow();
        assertFalse(flipped.accepted());
        assertTrue(flipped.reason().contains("Kafka"));
        RowOutcome untouched = reconciled.rows().stream().filter(r -> r.rowNumber() == 2).findFirst().orElseThrow();
        assertTrue(untouched.accepted());
    }

    @Test
    void reconciliationOnlyFlipsTheSpecificRowNotEveryRowSharingItsAccountNumber() {
        // Regression: correlating by account number would flip BOTH rows here
        // (two separate write-off transactions on the same account) even
        // though only row 3's publish actually failed.
        ImportOutcome outcome = new ImportOutcome("upload.xlsx", 2, 2, 0, List.of(
                new RowOutcome(2, "ACC-SAME", true, null),
                new RowOutcome(3, "ACC-SAME", true, null)));

        ImportOutcome reconciled = processor.reconcileWithPublishFailures(outcome, Set.of(3));

        assertEquals(1, reconciled.accepted());
        assertEquals(1, reconciled.rejected());
        assertTrue(reconciled.rows().stream().anyMatch(r -> r.rowNumber() == 2 && r.accepted()));
        assertTrue(reconciled.rows().stream().anyMatch(r -> r.rowNumber() == 3 && !r.accepted()));
    }
}
