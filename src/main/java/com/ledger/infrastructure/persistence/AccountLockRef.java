package com.ledger.infrastructure.persistence;

import com.ledger.domain.model.AccountRole;

import java.util.UUID;

public record AccountLockRef(UUID id, UUID customerId, AccountRole accountRole) {
}
