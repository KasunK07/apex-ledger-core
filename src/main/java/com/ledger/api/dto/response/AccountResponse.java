package com.ledger.api.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ledger.domain.model.Account;
import com.ledger.domain.model.AccountStatus;
import com.ledger.domain.model.MoneyAmounts;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountResponse(
        UUID id,
        UUID customerId,
        String currency,
        BigDecimal balance,
        AccountStatus status,
        Instant createdAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getCustomer().getId(),
                account.getCurrency(),
                MoneyAmounts.scale(account.getBalance()),
                account.getStatus(),
                account.getCreatedAt());
    }
}
