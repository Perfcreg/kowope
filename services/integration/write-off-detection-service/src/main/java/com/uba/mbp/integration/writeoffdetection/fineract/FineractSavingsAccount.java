package com.uba.mbp.integration.writeoffdetection.fineract;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code accountBalance} is the account's current balance (Fineract's
 * {@code summary.accountBalance}) — the figure RFP §3.13(bis)/User Story 2
 * calls the memo's "current balance". Not to be confused with any single
 * transaction's amount.
 */
public record FineractSavingsAccount(
        long id, String accountNo, String currencyCode, BigDecimal accountBalance,
        List<FineractSavingsTransaction> transactions) {
}
