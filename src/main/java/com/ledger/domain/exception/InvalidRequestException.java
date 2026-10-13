package com.ledger.domain.exception;

public class InvalidRequestException extends LedgerException {

    public InvalidRequestException(String message) {
        super("INVALID_REQUEST", message);
    }
}
