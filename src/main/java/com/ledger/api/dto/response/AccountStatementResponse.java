package com.ledger.api.dto.response;

import com.ledger.domain.model.AccountStatus;
import com.ledger.domain.model.EntryType;
import com.ledger.domain.model.JournalType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AccountStatementResponse(
        UUID accountId,
        UUID customerId,
        String currency,
        BigDecimal balance,
        AccountStatus status,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<StatementLineResponse> entries
) {
}
