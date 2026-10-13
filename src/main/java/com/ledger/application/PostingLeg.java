package com.ledger.application;

import com.ledger.domain.model.EntryType;

import java.math.BigDecimal;
import java.util.UUID;

public record PostingLeg(UUID accountId, EntryType entryType, BigDecimal amount, String currency) {
}
