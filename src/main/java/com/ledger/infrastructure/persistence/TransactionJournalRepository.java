package com.ledger.infrastructure.persistence;

import com.ledger.domain.model.TransactionJournal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionJournalRepository extends JpaRepository<TransactionJournal, Long> {

    Optional<TransactionJournal> findByReferenceId(UUID referenceId);
}
