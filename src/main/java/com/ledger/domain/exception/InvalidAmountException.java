package com.ledger.domain.exception;

public class InvalidAmountException extends LedgerException {

    public InvalidAmountException(String message) {
        super("INVALID_AMOUNT", message);
    }
}
