package com.uba.mbp.integration.writeoffdetection.fineract;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FineractSavingsTransaction(long id, String note, BigDecimal amount, LocalDate date) {
}
