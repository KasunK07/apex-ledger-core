package com.ledger.api.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ledger.domain.model.JournalStatus;
import com.ledger.domain.model.JournalType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CashMovementResponse(
        UUID referenceId,
        JournalType type,
        JournalStatus status,
        UUID accountId,
        BigDecimal amount,
        String currency,
        BigDecimal balance,
        String memo,
        Instant postedAt
) {
}
