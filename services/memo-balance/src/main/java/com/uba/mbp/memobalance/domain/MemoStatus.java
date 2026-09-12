package com.uba.mbp.memobalance.domain;

/** RFP §3.13(bis): a Memo account's lifecycle status. */
public enum MemoStatus {
    IMPORTED_PENDING_REVIEW,
    ACTIVE,
    LIQUIDATED
}
