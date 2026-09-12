package com.uba.mbp.memobalance.messaging;

/** Kafka topic names for this context's event contract (ADR-0001). */
public final class MemoTopics {
    public static final String MEMO_DETECTED = "mbp.integration.memo-detected";
    public static final String MEMO_BALANCE_ADJUSTED = "mbp.memo-balance.balance-adjusted";
    public static final String MEMO_LIQUIDATED = "mbp.memo-balance.liquidated";
    public static final String VISION_BALANCE_SYNCED = "mbp.integration.vision-balance-synced";

    private MemoTopics() {
    }
}
