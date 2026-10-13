package com.ledger.domain.exception;

public class IdempotencyKeyRequiredException extends LedgerException {

    public IdempotencyKeyRequiredException(String message) {
        super("IDEMPOTENCY_KEY_REQUIRED", message);
    }
}
