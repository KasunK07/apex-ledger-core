package com.ledger.domain.exception;

public abstract class LedgerException extends RuntimeException {

    private final String code;

    protected LedgerException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
