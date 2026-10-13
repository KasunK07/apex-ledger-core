package com.ledger.domain.exception;

public class TreasuryProtectedException extends LedgerException {

    public TreasuryProtectedException(String message) {
        super("TREASURY_PROTECTED", message);
    }
}
