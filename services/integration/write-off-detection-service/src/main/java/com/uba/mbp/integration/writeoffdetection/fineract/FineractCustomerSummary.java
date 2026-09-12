package com.uba.mbp.integration.writeoffdetection.fineract;

/**
 * A Fineract "Client" (their term for a bank customer) — named
 * {@code Customer} here, not {@code Client}, so it doesn't collide in meaning
 * with {@link FineractClient} (this adapter's own API gateway to Fineract).
 */
public record FineractCustomerSummary(long id, String displayName, String officeName) {
}
