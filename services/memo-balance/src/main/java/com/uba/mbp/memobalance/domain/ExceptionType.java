package com.uba.mbp.memobalance.domain;

/** RFP §3.10: the two exception kinds Transaction Services needs flagged. */
public enum ExceptionType {
    UNALLOCATED_PAYMENT,
    BALANCE_DISCREPANCY
}
