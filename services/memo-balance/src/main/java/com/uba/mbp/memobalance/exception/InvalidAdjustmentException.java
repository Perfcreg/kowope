package com.uba.mbp.memobalance.exception;

/** Ticket 04: an adjustment amount that isn't positive, or exceeds the current balance. */
public class InvalidAdjustmentException extends RuntimeException {
    public InvalidAdjustmentException(String message) {
        super(message);
    }
}
