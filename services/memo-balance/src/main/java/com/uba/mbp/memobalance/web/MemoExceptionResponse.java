package com.uba.mbp.memobalance.web;

import com.uba.mbp.memobalance.domain.ExceptionType;
import com.uba.mbp.memobalance.domain.MemoException;

import java.time.Instant;

public record MemoExceptionResponse(ExceptionType type, String detail, Instant raisedAt) {

    public static MemoExceptionResponse from(MemoException exception) {
        return new MemoExceptionResponse(exception.getType(), exception.getDetail(), exception.getRaisedAt());
    }
}
