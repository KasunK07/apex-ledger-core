package com.ledger.domain.exception;

import java.util.UUID;

public class AccountClosedException extends LedgerException {

    public AccountClosedException(UUID accountId) {
        super("ACCOUNT_CLOSED", "Account " + accountId + " is closed");
    }
}
