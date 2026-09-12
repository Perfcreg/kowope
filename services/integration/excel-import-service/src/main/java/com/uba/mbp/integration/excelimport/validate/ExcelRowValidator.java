package com.uba.mbp.integration.excelimport.validate;

import com.uba.mbp.integration.excelimport.event.MemoDetectedEvent;
import com.uba.mbp.integration.excelimport.parse.RawExcelRow;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Integration spec User Story 8: validates an uploaded row against the same
 * required fields as {@link MemoDetectedEvent} before anything is published —
 * a rejected row never reaches Kafka. {@code country} stays optional, matching
 * memo-balance's consumed-event contract (see its CONTEXT.md).
 */
@Component
public class ExcelRowValidator {

    public static final String SOURCE = "excel-import-service";

    public ValidatedRow validate(RawExcelRow row) {
        List<String> errors = new ArrayList<>();

        requireText(row.accountNumber(), "Account Number", errors);
        requireText(row.customerId(), "Customer ID", errors);
        requireText(row.branchSol(), "Branch/SOL", errors);
        requireText(row.currency(), "Currency", errors);
        requireText(row.postingReference(), "Posting Reference", errors);
        requireText(row.narration(), "Narration", errors);

        BigDecimal balance = parseBalance(row.balance(), errors);
        Instant transferDate = parseTransferDate(row.transferDate(), errors);

        if (!errors.isEmpty()) {
            return new ValidatedRow(
                    new RowOutcome(row.rowNumber(), row.accountNumber(), false, String.join("; ", errors)),
                    null);
        }

        MemoDetectedEvent event = new MemoDetectedEvent(
                row.accountNumber(), row.customerId(), row.branchSol(), row.currency(),
                row.postingReference(), row.narration(), balance, transferDate, SOURCE, row.country());
        return new ValidatedRow(new RowOutcome(row.rowNumber(), row.accountNumber(), true, null), event);
    }

    private void requireText(String value, String fieldName, List<String> errors) {
        if (value == null || value.isBlank()) {
            errors.add(fieldName + " is required");
        }
    }

    private BigDecimal parseBalance(String value, List<String> errors) {
        if (value == null || value.isBlank()) {
            errors.add("Balance is required");
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            errors.add("Balance '" + value + "' is not a valid number");
            return null;
        }
    }

    private Instant parseTransferDate(String value, List<String> errors) {
        if (value == null || value.isBlank()) {
            errors.add("Transfer Date is required");
            return null;
        }
        try {
            return LocalDate.parse(value.trim()).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException e) {
            errors.add("Transfer Date '" + value + "' is not a valid ISO date (yyyy-MM-dd)");
            return null;
        }
    }
}
