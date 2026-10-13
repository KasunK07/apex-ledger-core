package com.ledger.domain.exception;

public class CurrencyMismatchException extends LedgerException {

    public CurrencyMismatchException(String left, String right) {
        super("CURRENCY_MISMATCH", "Currencies " + left + " and " + right + " cannot be posted together");
    }
}
