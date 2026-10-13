package com.ledger.domain.exception;

public class DuplicateCustomerException extends LedgerException {

    public DuplicateCustomerException(String email) {
        super("CUSTOMER_ALREADY_EXISTS", "A customer with email " + email + " already exists");
    }
}
