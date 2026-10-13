package com.ledger.infrastructure.idempotency;

public final class IdempotencyKeys {

    public static final String HEADER = "Idempotency-Key";
    public static final String PATTERN = "^[A-Za-z0-9][A-Za-z0-9._:-]{7,199}$";

    private IdempotencyKeys() {
    }
}
