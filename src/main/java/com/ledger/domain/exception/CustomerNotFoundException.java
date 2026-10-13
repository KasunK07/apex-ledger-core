package com.ledger.domain.exception;

import java.util.UUID;

public class CustomerNotFoundException extends LedgerException {

    public CustomerNotFoundException(UUID customerId) {
        super("CUSTOMER_NOT_FOUND", "Customer " + customerId + " was not found");
    }
}
