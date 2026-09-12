package com.uba.mbp.integration.excelimport.validate;

import com.uba.mbp.integration.excelimport.parse.RawExcelRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelRowValidatorTest {

    private final ExcelRowValidator validator = new ExcelRowValidator();

    private RawExcelRow validRow() {
        return new RawExcelRow(2, "ACC-001", "CUST-1", "SOL-001", "NGN", "TXN-1",
                "written off", "50000.00", "2026-01-15", "NG");
    }

    @Test
    void acceptsAFullyPopulatedRowAndBuildsTheMemoDetectedEventShape() {
        ValidatedRow result = validator.validate(validRow());

        assertTrue(result.outcome().accepted());
        assertNull(result.outcome().reason());
        assertNotNull(result.event());
        assertEquals("ACC-001", result.event().accountNumber());
        assertEquals(0, new BigDecimal("50000.00").compareTo(result.event().balance()));
        assertEquals(Instant.parse("2026-01-15T00:00:00Z"), result.event().transferDate());
        assertEquals("excel-import-service", result.event().source());
        assertEquals("NG", result.event().country());
    }

    @Test
    void acceptsARowWithNoCountrySinceItIsOptional() {
        RawExcelRow row = new RawExcelRow(2, "ACC-001", "CUST-1", "SOL-001", "NGN", "TXN-1",
                "written off", "50000.00", "2026-01-15", null);

        ValidatedRow result = validator.validate(row);

        assertTrue(result.outcome().accepted());
        assertNull(result.event().country());
    }

    @Test
    void rejectsARowMissingARequiredTextField() {
        RawExcelRow row = new RawExcelRow(3, null, "CUST-1", "SOL-001", "NGN", "TXN-1",
                "written off", "50000.00", "2026-01-15", "NG");

        ValidatedRow result = validator.validate(row);

        assertFalse(result.outcome().accepted());
        assertNull(result.event());
        assertTrue(result.outcome().reason().contains("Account Number is required"));
    }

    @Test
    void rejectsANonNumericBalance() {
        RawExcelRow row = new RawExcelRow(4, "ACC-001", "CUST-1", "SOL-001", "NGN", "TXN-1",
                "written off", "not-a-number", "2026-01-15", "NG");

        ValidatedRow result = validator.validate(row);

        assertFalse(result.outcome().accepted());
        assertTrue(result.outcome().reason().contains("Balance"));
    }

    @Test
    void rejectsAnUnparseableTransferDate() {
        RawExcelRow row = new RawExcelRow(5, "ACC-001", "CUST-1", "SOL-001", "NGN", "TXN-1",
                "written off", "50000.00", "15th January 2026", "NG");

        ValidatedRow result = validator.validate(row);

        assertFalse(result.outcome().accepted());
        assertTrue(result.outcome().reason().contains("Transfer Date"));
    }

    @Test
    void reportsEveryMissingFieldTogetherNotJustTheFirst() {
        RawExcelRow row = new RawExcelRow(6, null, null, "SOL-001", "NGN", "TXN-1",
                "written off", "50000.00", "2026-01-15", "NG");

        ValidatedRow result = validator.validate(row);

        assertTrue(result.outcome().reason().contains("Account Number is required"));
        assertTrue(result.outcome().reason().contains("Customer ID is required"));
    }

    @Test
    void toleratesThousandsSeparatorsInBalance() {
        RawExcelRow row = new RawExcelRow(7, "ACC-001", "CUST-1", "SOL-001", "NGN", "TXN-1",
                "written off", "6,125,000.00", "2026-01-15", "NG");

        ValidatedRow result = validator.validate(row);

        assertTrue(result.outcome().accepted());
        assertEquals(0, new BigDecimal("6125000.00").compareTo(result.event().balance()));
    }
}
