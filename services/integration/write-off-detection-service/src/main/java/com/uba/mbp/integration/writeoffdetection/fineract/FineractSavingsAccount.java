package com.uba.mbp.integration.writeoffdetection.fineract;

import java.util.List;

public record FineractSavingsAccount(long id, String accountNo, String currencyCode, List<FineractSavingsTransaction> transactions) {
}
