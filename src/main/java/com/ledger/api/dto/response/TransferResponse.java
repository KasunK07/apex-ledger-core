package com.ledger.api.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ledger.domain.model.JournalStatus;
import com.ledger.domain.model.JournalType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TransferResponse(
        UUID referenceId,
        JournalType type,
        JournalStatus status,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        String currency,
        BigDecimal sourceBalance,
        BigDecimal destinationBalance,
        String memo,
        Instant postedAt
) {
}
