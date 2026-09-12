package com.uba.mbp.integration.excelimport.parse;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * Real Apache POI OOXML (.xlsx) parsing — no hand-rolled or mocked reader.
 * Header-name-based column mapping: the first row is read as headers and
 * matched case/whitespace/punctuation-insensitively, so the Maxim team's own
 * column order and casing don't have to match this service's assumptions.
 * Produces raw text per cell only; {@code validate.ExcelRowValidator} owns
 * type conversion and business validation.
 */
@Component
public class ExcelWorkbookParser {

    private static final Map<String, String> REQUIRED_COLUMNS = Map.ofEntries(
            Map.entry("accountnumber", "accountNumber"),
            Map.entry("customerid", "customerId"),
            Map.entry("branchsol", "branchSol"),
            Map.entry("currency", "currency"),
            Map.entry("postingreference", "postingReference"),
            Map.entry("narration", "narration"),
            Map.entry("balance", "balance"),
            Map.entry("transferdate", "transferDate"));

    private static final String OPTIONAL_COUNTRY_COLUMN = "country";

    public List<RawExcelRow> parse(InputStream inputStream) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            Map<String, Integer> columnIndex = mapHeaderRow(sheet.getRow(sheet.getFirstRowNum()), formatter);

            List<RawExcelRow> rows = new ArrayList<>();
            for (int rowNum = sheet.getFirstRowNum() + 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null || isBlankRow(row, formatter)) {
                    continue;
                }
                rows.add(new RawExcelRow(
                        rowNum + 1,
                        cell(row, columnIndex, "accountNumber", formatter),
                        cell(row, columnIndex, "customerId", formatter),
                        cell(row, columnIndex, "branchSol", formatter),
                        cell(row, columnIndex, "currency", formatter),
                        cell(row, columnIndex, "postingReference", formatter),
                        cell(row, columnIndex, "narration", formatter),
                        cell(row, columnIndex, "balance", formatter),
                        transferDateCell(row, columnIndex.get("transferDate"), formatter),
                        cell(row, columnIndex, "country", formatter)));
            }
            return rows;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read uploaded workbook as .xlsx", e);
        }
    }

    private Map<String, Integer> mapHeaderRow(Row headerRow, DataFormatter formatter) {
        if (headerRow == null) {
            throw new IllegalArgumentException("Uploaded workbook has no header row");
        }
        Map<String, Integer> index = new HashMap<>();
        for (Cell cell : headerRow) {
            String normalized = normalize(formatter.formatCellValue(cell));
            String field = REQUIRED_COLUMNS.get(normalized);
            if (field != null) {
                index.put(field, cell.getColumnIndex());
            } else if (normalized.equals(OPTIONAL_COUNTRY_COLUMN)) {
                index.put("country", cell.getColumnIndex());
            }
        }
        List<String> missing = REQUIRED_COLUMNS.values().stream()
                .filter(field -> !index.containsKey(field))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Uploaded workbook is missing required column(s): " + missing);
        }
        return index;
    }

    private String cell(Row row, Map<String, Integer> columnIndex, String field, DataFormatter formatter) {
        Integer index = columnIndex.get(field);
        if (index == null) {
            return null;
        }
        Cell cell = row.getCell(index);
        if (cell == null) {
            return null;
        }
        String value = formatter.formatCellValue(cell).trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * A real Excel date cell is stored as a number; reading it through
     * {@link DataFormatter} would produce a locale-dependent string (e.g.
     * "1/15/26") that {@code ExcelRowValidator} can't reliably parse. When the
     * cell is genuinely date-formatted, extract the date directly and render
     * it as ISO-8601 instead; a text cell (the Maxim team typed "2026-01-15")
     * passes through unchanged.
     */
    private String transferDateCell(Row row, Integer index, DataFormatter formatter) {
        if (index == null) {
            return null;
        }
        Cell cellValue = row.getCell(index);
        if (cellValue == null) {
            return null;
        }
        if (cellValue.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cellValue)) {
            return cellValue.getLocalDateTimeCellValue().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        String value = formatter.formatCellValue(cellValue).trim();
        return value.isEmpty() ? null : value;
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        for (Cell cell : row) {
            if (!formatter.formatCellValue(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String normalize(String header) {
        return header == null ? "" : header.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
