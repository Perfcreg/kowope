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

/**
 * Plain orchestration bean, no Camel/Spring-web dependency — independently
 * testable, matching the write-off-detection-service/vision-etl-connector
 * pattern of keeping business logic out of the route. Parses, validates, and
 * audits (integration spec User Story 13) every row of an uploaded workbook;
 * the Camel route owns publishing the accepted events to Kafka.
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

        auditLogger.record(new AuditEvent(
                clock.instant(), actor, "EXCEL_UPLOAD_PROCESSED", "ExcelImport", fileName,
                ExcelRowValidator.SOURCE,
                "accepted=" + outcome.accepted() + " rejected=" + outcome.rejected()));

        return new ImportResult(acceptedEvents, outcome);
    }
}
