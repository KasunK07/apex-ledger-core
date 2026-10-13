package com.ledger.domain.exception;

import java.util.UUID;

public class CustomerKycPendingException extends LedgerException {

    public CustomerKycPendingException(UUID customerId) {
        super("CUSTOMER_KYC_PENDING", "Customer " + customerId + " has not completed KYC");
    }
}
