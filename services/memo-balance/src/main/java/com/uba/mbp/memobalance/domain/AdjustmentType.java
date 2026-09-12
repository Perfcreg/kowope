package com.uba.mbp.memobalance.domain;

/** RFP §3.1/§3.10: the two ways a memo balance is adjusted downward. */
public enum AdjustmentType {
    PARTIAL_PAYMENT,
    WRITE_OFF_APPROVED
}
