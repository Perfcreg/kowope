package com.uba.mbp.integration.excelimport.fixtures;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Builds real .xlsx binaries with Apache POI for tests — no static checked-in
 * fixture file, no mocked reader. Both the write path ({@code XSSFWorkbook}
 * here) and the read path ({@code ExcelWorkbookParser} under test) are real
 * POI OOXML code, so a roundtrip through this helper exercises the actual
 * binary format, just without an opaque binary asset in git.
 */
public final class ExcelFixtures {

    public static final List<String> STANDARD_HEADERS = List.of(
            "Account Number", "Customer ID", "Branch/SOL", "Currency",
            "Posting Reference", "Narration", "Balance", "Transfer Date", "Country");

    private ExcelFixtures() {
    }

    public static InputStream workbook(List<String> headers, List<List<String>> dataRows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Memo Upload");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                headerRow.createCell(i).setCellValue(headers.get(i));
            }
            for (int r = 0; r < dataRows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<String> values = dataRows.get(r);
                for (int c = 0; c < values.size(); c++) {
                    if (values.get(c) != null) {
                        row.createCell(c).setCellValue(values.get(c));
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static InputStream standardWorkbook(List<List<String>> dataRows) {
        return workbook(STANDARD_HEADERS, dataRows);
    }
}
