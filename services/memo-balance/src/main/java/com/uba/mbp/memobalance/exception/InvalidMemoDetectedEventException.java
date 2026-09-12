package com.uba.mbp.memobalance.exception;

/** Thrown when a {@code MemoDetected} event is missing a field RFP §3.13(bis) requires. */
public class InvalidMemoDetectedEventException extends RuntimeException {
    public InvalidMemoDetectedEventException(String message) {
        super(message);
    }
}
