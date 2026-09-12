package com.uba.mbp.integration.excelimport.messaging;

/** Kafka topic names this adapter publishes to (ADR-0001). */
public final class IntegrationTopics {
    public static final String MEMO_DETECTED = "mbp.integration.memo-detected";

    private IntegrationTopics() {
    }
}
