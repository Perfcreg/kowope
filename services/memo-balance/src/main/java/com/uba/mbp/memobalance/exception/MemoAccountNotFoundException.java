package com.uba.mbp.memobalance.exception;

public class MemoAccountNotFoundException extends RuntimeException {
    public MemoAccountNotFoundException(String accountNumber) {
        super("No Memo account found for account number: " + accountNumber);
    }
}
