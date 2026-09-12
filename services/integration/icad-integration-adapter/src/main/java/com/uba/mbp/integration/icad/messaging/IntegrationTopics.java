package com.uba.mbp.integration.icad.messaging;

/** Kafka topic names this adapter publishes to (ADR-0001). */
public final class IntegrationTopics {
    public static final String ICAD_CLEARANCE_OUTCOME = "mbp.integration.icad-clearance-outcome";

    private IntegrationTopics() {
    }
}
