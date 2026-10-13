package com.ledger.domain.exception;

import java.util.UUID;

public class CustomerSuspendedException extends LedgerException {

    public CustomerSuspendedException(UUID customerId) {
        super("CUSTOMER_SUSPENDED", "Customer " + customerId + " is suspended");
    }
}
