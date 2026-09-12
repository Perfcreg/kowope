package com.uba.mbp.integration.visionetl.fineract;

import java.util.List;

/**
 * ADR-0013's swap seam: "pointing one at a real Vision system later... is a
 * one-line config change per adapter, not a shared-client refactor" only
 * holds if this is an interface a real-Vision implementation can substitute
 * — {@link FineractHttpClient} is the Fineract-shaped implementation of it.
 */
public interface FineractClient {

    List<Long> listActiveClientIds();

    List<FineractAccountBalance> listSavingsAccountBalances(long clientId);
}
