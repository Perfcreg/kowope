package com.uba.mbp.integration.excelimport.parse;

import com.uba.mbp.integration.excelimport.fixtures.ExcelFixtures;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real Apache POI read against a real (in-memory generated) .xlsx binary — no mocked reader. */
class ExcelWorkbookParserTest {

    private final ExcelWorkbookParser parser = new ExcelWorkbookParser();

    @Test
    void parsesEveryDataRowByHeaderNameRegardlessOfColumnOrder() {
        List<String> shuffledHeaders = List.of(
                "Currency", "Account Number", "Narration", "Customer ID",
                "Balance", "Branch/SOL", "Transfer Date", "Posting Reference", "Country");
        InputStream workbook = ExcelFixtures.workbook(shuffledHeaders, List.of(
                List.of("NGN", "ACC-001", "written off", "CUST-1", "50000.00", "SOL-001", "2026-01-15", "TXN-1", "NG")));

        List<RawExcelRow> rows = parser.parse(workbook);

        assertEquals(1, rows.size());
        RawExcelRow row = rows.get(0);
        assertEquals("ACC-001", row.accountNumber());
        assertEquals("CUST-1", row.customerId());
        assertEquals("SOL-001", row.branchSol());
        assertEquals("NGN", row.currency());
        assertEquals("TXN-1", row.postingReference());
        assertEquals("written off", row.narration());
        assertEquals("50000.00", row.balance());
        assertEquals("2026-01-15", row.transferDate());
        assertEquals("NG", row.country());
    }

    @Test
    void skipsBlankRowsBetweenDataRows() {
        InputStream workbook = ExcelFixtures.standardWorkbook(Arrays.asList(
                List.of("ACC-001", "CUST-1", "SOL-001", "NGN", "TXN-1", "written off", "1000", "2026-01-15", "NG"),
                Arrays.asList(null, null, null, null, null, null, null, null, null),
                List.of("ACC-002", "CUST-2", "SOL-002", "NGN", "TXN-2", "written off", "2000", "2026-01-16", "NG")));

        List<RawExcelRow> rows = parser.parse(workbook);

        assertEquals(2, rows.size());
        assertEquals("ACC-001", rows.get(0).accountNumber());
        assertEquals("ACC-002", rows.get(1).accountNumber());
    }

    @Test
    void treatsAMissingOptionalCountryCellAsNull() {
        InputStream workbook = ExcelFixtures.standardWorkbook(List.of(
                Arrays.asList("ACC-001", "CUST-1", "SOL-001", "NGN", "TXN-1", "written off", "1000", "2026-01-15", null)));

        List<RawExcelRow> rows = parser.parse(workbook);

        assertNull(rows.get(0).country());
    }

    @Test
    void rejectsAWorkbookMissingARequiredColumn() {
        List<String> incompleteHeaders = List.of(
                "Account Number", "Customer ID", "Branch/SOL", "Currency", "Narration", "Balance", "Transfer Date");
        InputStream workbook = ExcelFixtures.workbook(incompleteHeaders, List.of());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parser.parse(workbook));
        assertTrue(e.getMessage().contains("postingReference"));
    }

    @Test
    void readsARealExcelDateCellAsIsoWithoutLocaleAmbiguity() {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.CreationHelper helper = wb.getCreationHelper();
            org.apache.poi.ss.usermodel.CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("m/d/yy"));

            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet();
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            for (int i = 0; i < ExcelFixtures.STANDARD_HEADERS.size(); i++) {
                header.createCell(i).setCellValue(ExcelFixtures.STANDARD_HEADERS.get(i));
            }
            org.apache.poi.ss.usermodel.Row dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue("ACC-001");
            dataRow.createCell(1).setCellValue("CUST-1");
            dataRow.createCell(2).setCellValue("SOL-001");
            dataRow.createCell(3).setCellValue("NGN");
            dataRow.createCell(4).setCellValue("TXN-1");
            dataRow.createCell(5).setCellValue("written off");
            dataRow.createCell(6).setCellValue(1000.0);
            org.apache.poi.ss.usermodel.Cell dateCell = dataRow.createCell(7);
            dateCell.setCellValue(java.time.LocalDate.of(2026, 1, 15));
            dateCell.setCellStyle(dateStyle);

            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            wb.write(out);

            List<RawExcelRow> rows = parser.parse(new java.io.ByteArrayInputStream(out.toByteArray()));

            assertEquals("2026-01-15", rows.get(0).transferDate());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
