package com.ledger.domain.exception;

public class SameAccountTransferException extends LedgerException {

    public SameAccountTransferException() {
        super("SAME_ACCOUNT_TRANSFER", "Source and destination accounts must be different");
    }
}
