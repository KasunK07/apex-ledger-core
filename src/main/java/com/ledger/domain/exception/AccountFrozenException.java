package com.ledger.domain.exception;

import java.util.UUID;

public class AccountFrozenException extends LedgerException {

    public AccountFrozenException(UUID accountId) {
        super("ACCOUNT_FROZEN", "Account " + accountId + " is frozen");
    }
}
