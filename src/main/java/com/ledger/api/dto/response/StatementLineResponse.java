package com.ledger.api.dto.response;

import com.ledger.domain.model.EntryType;
import com.ledger.domain.model.JournalType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StatementLineResponse(
        Long entryId,
        UUID journalReferenceId,
        JournalType journalType,
        EntryType entryType,
        BigDecimal amount,
        String currency,
        BigDecimal runningBalance,
        String memo,
        Instant postedAt
) {
}
