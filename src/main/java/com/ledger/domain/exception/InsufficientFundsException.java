package com.ledger.domain.exception;

import java.util.UUID;

public class InsufficientFundsException extends LedgerException {

    public InsufficientFundsException(UUID accountId, String currency) {
        super("INSUFFICIENT_FUNDS",
                "Insufficient funds on account " + accountId + " (" + currency + ")");
    }
}
