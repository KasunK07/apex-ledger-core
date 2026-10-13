package com.ledger.domain.exception;

public class UnsupportedCurrencyException extends LedgerException {

    public UnsupportedCurrencyException(String currency) {
        super("UNSUPPORTED_CURRENCY", "No treasury account is configured for " + currency);
    }
}
