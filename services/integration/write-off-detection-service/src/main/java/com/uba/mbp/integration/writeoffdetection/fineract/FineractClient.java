package com.uba.mbp.integration.writeoffdetection.fineract;

import java.util.List;

/**
 * ADR-0012's swap seam: "swapping to real Finacle later means replacing
 * {@code FineractClient}'s implementation, not the detection logic or the
 * {@code MemoDetected} event contract" only holds if this is an interface a
 * real-Finacle implementation can substitute — {@link FineractHttpClient} is
 * the Fineract-shaped implementation of it.
 */
public interface FineractClient {

    List<FineractClientSummary> listActiveClients();

    List<Long> listSavingsAccountIds(long clientId);

    FineractSavingsAccount getSavingsAccountWithTransactions(long savingsAccountId);
}
