package com.ledger.domain.exception;

public class IdempotencyConflictException extends LedgerException {

    public IdempotencyConflictException() {
        super("IDEMPOTENCY_CONFLICT",
                "Idempotency-Key was already used with a different request");
    }
}
