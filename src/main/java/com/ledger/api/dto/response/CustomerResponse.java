package com.ledger.api.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ledger.domain.model.Customer;
import com.ledger.domain.model.CustomerStatus;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerResponse(
        UUID id,
        String email,
        String fullName,
        CustomerStatus status,
        Instant createdAt
) {
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getEmail(),
                customer.getFullName(),
                customer.getStatus(),
                customer.getCreatedAt());
    }
}
