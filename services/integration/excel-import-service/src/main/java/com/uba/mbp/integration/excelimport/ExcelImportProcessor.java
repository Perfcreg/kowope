package com.uba.mbp.integration.excelimport;

import com.uba.mbp.audit.AuditEvent;
import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.integration.excelimport.event.MemoDetectedEvent;
import com.uba.mbp.integration.excelimport.parse.ExcelWorkbookParser;
import com.uba.mbp.integration.excelimport.parse.RawExcelRow;
import com.uba.mbp.integration.excelimport.validate.ExcelRowValidator;
import com.uba.mbp.integration.excelimport.validate.ImportOutcome;
import com.uba.mbp.integration.excelimport.validate.ImportResult;
import com.uba.mbp.integration.excelimport.validate.RowOutcome;
import com.uba.mbp.integration.excelimport.validate.ValidatedRow;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Plain orchestration bean, no Camel/Spring-web dependency — independently
 * testable, matching the write-off-detection-service/vision-etl-connector
 * pattern of keeping business logic out of the route. Parses, validates, and
 * audits (integration spec User Story 13) every row of an uploaded workbook;
 * the Camel route owns publishing the accepted events to Kafka and calls
 * {@link #reconcileWithPublishFailures} afterwards so the outcome reported to
 * the caller reflects what actually reached Kafka, not just what validated.
 */
@Component
public class ExcelImportProcessor {

    private final ExcelWorkbookParser parser;
    private final ExcelRowValidator validator;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public ExcelImportProcessor(ExcelWorkbookParser parser, ExcelRowValidator validator,
                                 AuditLogger auditLogger, Clock clock) {
        this.parser = parser;
        this.validator = validator;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    public ImportResult process(String actor, String fileName, InputStream fileContent) {
        List<RawExcelRow> rows = parser.parse(fileContent);

        List<MemoDetectedEvent> acceptedEvents = new ArrayList<>();
        List<RowOutcome> rowOutcomes = new ArrayList<>();

        for (RawExcelRow row : rows) {
            ValidatedRow validated = validator.validate(row);
            rowOutcomes.add(validated.outcome());
            if (validated.outcome().accepted()) {
                acceptedEvents.add(validated.event());
                auditLogger.record(new AuditEvent(
                        clock.instant(), actor, "MEMO_DETECTED_VIA_EXCEL_IMPORT",
                        "MemoAccount", validated.event().accountNumber(), ExcelRowValidator.SOURCE,
                        "row " + row.rowNumber() + " of " + fileName));
            }
        }

        long rejected = rowOutcomes.stream().filter(outcome -> !outcome.accepted()).count();
        ImportOutcome outcome = new ImportOutcome(
                fileName, rows.size(), acceptedEvents.size(), (int) rejected, rowOutcomes);

        return new ImportResult(acceptedEvents, outcome);
    }

    /**
     * A row that validated but whose Kafka publish failed after exhausted
     * retries is flipped from accepted to rejected here — the DLQ (see
     * {@code ExcelImportRoute}) means the data isn't lost, but the Maxim user
     * must be told it didn't reach memo-balance, not that it succeeded.
     */
    public ImportOutcome reconcileWithPublishFailures(ImportOutcome outcome, Set<String> failedAccountNumbers) {
        if (failedAccountNumbers.isEmpty()) {
            return outcome;
        }
        List<RowOutcome> reconciled = new ArrayList<>();
        int accepted = 0;
        for (RowOutcome row : outcome.rows()) {
            if (row.accepted() && failedAccountNumbers.contains(row.accountNumber())) {
                reconciled.add(new RowOutcome(row.rowNumber(), row.accountNumber(), false,
                        "Validated successfully but failed to publish to Kafka after retries — contact support"));
            } else {
                reconciled.add(row);
                if (row.accepted()) {
                    accepted++;
                }
            }
        }
        return new ImportOutcome(outcome.fileName(), outcome.totalRows(), accepted,
                reconciled.size() - accepted, reconciled);
    }

    /** The file-level summary audit is written after reconciliation so it reflects the final, true outcome. */
    public void auditFinalOutcome(String actor, ImportOutcome outcome) {
        auditLogger.record(new AuditEvent(
                clock.instant(), actor, "EXCEL_UPLOAD_PROCESSED", "ExcelImport", outcome.fileName(),
                ExcelRowValidator.SOURCE,
                "accepted=" + outcome.accepted() + " rejected=" + outcome.rejected()));
    }
}
