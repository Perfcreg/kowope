package com.uba.mbp.integration.writeoffdetection.fineract;

import java.time.LocalDate;

/**
 * No {@code amount} field: the RFP's "current balance" (User Story 2) is the
 * account's balance ({@link FineractSavingsAccount#accountBalance()}), never
 * a single transaction's own amount — keeping it here would misleadingly
 * suggest it's usable for that.
 */
public record FineractSavingsTransaction(long id, String note, LocalDate date) {
}
