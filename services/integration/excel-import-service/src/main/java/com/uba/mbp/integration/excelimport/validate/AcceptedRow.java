package com.uba.mbp.integration.excelimport.validate;

import com.uba.mbp.integration.excelimport.event.MemoDetectedEvent;

/**
 * Pairs an accepted event with its source row number so a publish failure
 * can be attributed back to the exact row that failed — {@code accountNumber}
 * alone is NOT a safe correlation key: two different rows in the same upload
 * can legitimately share an account number (e.g. two separate write-off
 * transactions on the same account), and correlating by account number would
 * incorrectly flip every row for that account to rejected if only one of
 * them actually failed to publish.
 */
public record AcceptedRow(int rowNumber, MemoDetectedEvent event) {
}
