package com.uba.mbp.sharedplatform.notification.messaging;

/** Kafka topics this service consumes — owned/published by other contexts (ADR-0001). */
public final class NotificationTopics {
    public static final String MEMO_LIQUIDATED = "mbp.memo-balance.liquidated";
    public static final String ICAD_CLEARANCE_OUTCOME = "mbp.integration.icad-clearance-outcome";

    private NotificationTopics() {
    }
}
