package com.ledger.domain.exception;

import java.util.UUID;

public class AccountNotFoundException extends LedgerException {

    public AccountNotFoundException(UUID accountId) {
        super("ACCOUNT_NOT_FOUND", "Account " + accountId + " was not found");
    }
}
