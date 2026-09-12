package com.uba.mbp.memobalance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Ticket 06: a flagged balance/payment exception on a Memo account, visible to Transaction Services. */
@Entity
@Table(name = "memo_exception")
public class MemoException {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "memo_account_id", nullable = false)
    private UUID memoAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExceptionType type;

    @Column(nullable = false, columnDefinition = "text")
    private String detail;

    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt;

    protected MemoException() {
        // JPA
    }

    public static MemoException raise(UUID memoAccountId, ExceptionType type, String detail, Instant raisedAt) {
        MemoException exception = new MemoException();
        exception.memoAccountId = memoAccountId;
        exception.type = type;
        exception.detail = detail;
        exception.raisedAt = raisedAt;
        return exception;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMemoAccountId() {
        return memoAccountId;
    }

    public ExceptionType getType() {
        return type;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getRaisedAt() {
        return raisedAt;
    }
}
