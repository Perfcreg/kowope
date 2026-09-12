package com.uba.mbp.integration.excelimport.validate;

import java.util.List;

/** The reply to the Maxim team user's upload (integration spec User Story 10). */
public record ImportOutcome(String fileName, int totalRows, int accepted, int rejected, List<RowOutcome> rows) {
}
